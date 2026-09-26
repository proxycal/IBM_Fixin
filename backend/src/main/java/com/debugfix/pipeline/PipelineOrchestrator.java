package com.debugfix.pipeline;

import com.debugfix.dto.*;
import com.debugfix.service.*;
import com.debugfix.service.BuildRunnerService.TestVerificationResult;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.util.List;

/**
 * Owns the five-stage debug-to-fix pipeline:
 * <ol>
 *   <li>Clone the repository.</li>
 *   <li>Extract relevant source context.</li>
 *   <li>Ask watsonx.ai for the root cause.</li>
 *   <li>Ask watsonx.ai for a fix (full file content as JSON).</li>
 *   <li>Apply the patch, ask for a regression test, write it, then run the suite.</li>
 * </ol>
 * The cloned repo is always deleted in a {@code finally} block.
 */
@Component
public class PipelineOrchestrator {

    // -------------------------------------------------------------------------
    // System prompts
    // -------------------------------------------------------------------------

    static final String PROMPT_ROOT_CAUSE =
            "You are an expert Java software engineer. " +
            "Given the following source files from a repository and a bug description, " +
            "identify the root cause of the bug. " +
            "Be concise: 2–4 sentences describing exactly what is wrong and why.";

    static final String PROMPT_FIX =
            "You are an expert Java software engineer. " +
            "Given the following source files and the identified root cause, " +
            "return a JSON array of every file that needs to change to fix the bug. " +
            "Each element must have exactly two fields: \"path\" (relative to repo root, " +
            "forward slashes) and \"content\" (the full corrected file content as a string). " +
            "Respond with ONLY the JSON array — no explanation, no markdown fences.";

    static final String PROMPT_TEST =
            "You are an expert Java software engineer specialising in JUnit 5. " +
            "Given the following patched source file(s) and the bug that was fixed, " +
            "write a JUnit 5 regression test that would have caught this bug. " +
            "Return a single JSON object with fields \"path\" (relative to repo root, " +
            "e.g. src/test/java/com/example/FooTest.java) and \"content\" (full test class). " +
            "Respond with ONLY the JSON object — no explanation, no markdown fences.";

    // -------------------------------------------------------------------------
    // Dependencies
    // -------------------------------------------------------------------------

    private final GitService              gitService;
    private final ContextExtractorService contextExtractor;
    private final WatsonxService          watsonxService;
    private final PatchApplierService     patchApplier;
    private final TestWriterService       testWriter;
    private final BuildRunnerService      buildRunner;
    private final ObjectMapper            mapper = new ObjectMapper();

    public PipelineOrchestrator(GitService gitService,
                                ContextExtractorService contextExtractor,
                                WatsonxService watsonxService,
                                PatchApplierService patchApplier,
                                TestWriterService testWriter,
                                BuildRunnerService buildRunner) {
        this.gitService       = gitService;
        this.contextExtractor = contextExtractor;
        this.watsonxService   = watsonxService;
        this.patchApplier     = patchApplier;
        this.testWriter       = testWriter;
        this.buildRunner      = buildRunner;
    }

    // -------------------------------------------------------------------------
    // Pipeline entry point
    // -------------------------------------------------------------------------

    public PipelineResultDTO run(PipelineRequestDTO request) throws Exception {
        Path repoPath = gitService.cloneRepo(request.getRepoUrl(),
                                             request.getBranch() != null ? request.getBranch() : "main");
        try {
            return execute(repoPath, request);
        } finally {
            gitService.deleteRepo(repoPath);
        }
    }

    // -------------------------------------------------------------------------
    // Stages
    // -------------------------------------------------------------------------

    private PipelineResultDTO execute(Path repoPath, PipelineRequestDTO request) throws Exception {
        PipelineResultDTO result = new PipelineResultDTO();

        // Stage 2: extract context
        String context = contextExtractor.extract(repoPath, request.getBugDescription());

        // Stage 3: root cause
        String rootCauseUserMsg =
                "Bug description: " + request.getBugDescription() + "\n\n" +
                "Source files:\n" + context;
        String rootCause = watsonxService.generate(PROMPT_ROOT_CAUSE, rootCauseUserMsg);
        result.setRootCause(rootCause);

        // Stage 4: fix proposal
        String fixUserMsg =
                "Root cause: " + rootCause + "\n\n" +
                "Source files:\n" + context;
        String fixJson = watsonxService.generate(PROMPT_FIX, fixUserMsg);
        List<PatchedFile> patches = parsePatches(fixJson);
        result.setPatchedFiles(patches);
        result.setFixDescription(rootCause); // summary reuse; enriched if desired

        // Stage 4b: apply patches
        patchApplier.applyPatch(repoPath, patches);

        // Stage 5a: generate test
        StringBuilder patchedContent = new StringBuilder();
        for (PatchedFile p : patches) {
            patchedContent.append("// FILE: ").append(p.relativePath()).append("\n")
                          .append(p.content()).append("\n\n");
        }
        String testUserMsg =
                "Fixed bug: " + rootCause + "\n\n" +
                "Patched files:\n" + patchedContent;
        String testJson = watsonxService.generate(PROMPT_TEST, testUserMsg);
        GeneratedTest generatedTest = parseGeneratedTest(testJson);
        result.setGeneratedTest(generatedTest);

        // Stage 5b: write test
        testWriter.writeTest(repoPath, generatedTest);

        // Stage 5c: run test suite
        TestVerificationResult verification;
        try {
            verification = buildRunner.runTests(repoPath);
        } catch (BuildRunnerService.UnsupportedBuildToolException e) {
            verification = new TestVerificationResult(false,
                    "Skipped: " + e.getMessage() + ". No Maven or Gradle build file found in the repository.");
        }
        result.setTestVerification(verification);

        return result;
    }

    // -------------------------------------------------------------------------
    // Response parsers
    // -------------------------------------------------------------------------

    private List<PatchedFile> parsePatches(String json) {
        try {
            List<JsonNode> nodes = mapper.readValue(json, new TypeReference<>() {});
            return nodes.stream()
                    .map(n -> new PatchedFile(n.get("path").asText(), n.get("content").asText()))
                    .toList();
        } catch (Exception e) {
            throw new RuntimeException("Failed to parse fix JSON from LLM: " + json, e);
        }
    }

    private GeneratedTest parseGeneratedTest(String json) {
        try {
            JsonNode node = mapper.readTree(json);
            return new GeneratedTest(node.get("path").asText(), node.get("content").asText());
        } catch (Exception e) {
            throw new RuntimeException("Failed to parse test JSON from LLM: " + json, e);
        }
    }
}
