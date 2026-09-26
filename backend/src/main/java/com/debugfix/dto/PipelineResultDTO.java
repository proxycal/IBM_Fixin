package com.debugfix.dto;

import com.debugfix.service.BuildRunnerService.TestVerificationResult;

import java.util.List;

/**
 * Outbound response DTO for {@code POST /api/pipeline/run}.
 */
public class PipelineResultDTO {

    private String rootCause;
    private String fixDescription;
    private List<PatchedFile> patchedFiles;
    private GeneratedTest generatedTest;
    private TestVerificationResult testVerification;

    public String getRootCause()                        { return rootCause; }
    public void   setRootCause(String v)                { this.rootCause = v; }

    public String getFixDescription()                   { return fixDescription; }
    public void   setFixDescription(String v)           { this.fixDescription = v; }

    public List<PatchedFile> getPatchedFiles()          { return patchedFiles; }
    public void   setPatchedFiles(List<PatchedFile> v)  { this.patchedFiles = v; }

    public GeneratedTest getGeneratedTest()             { return generatedTest; }
    public void   setGeneratedTest(GeneratedTest v)     { this.generatedTest = v; }

    public TestVerificationResult getTestVerification()          { return testVerification; }
    public void   setTestVerification(TestVerificationResult v)  { this.testVerification = v; }
}
