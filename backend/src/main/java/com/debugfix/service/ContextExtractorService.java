package com.debugfix.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Stream;

/**
 * Walks a cloned repository and assembles a context string (concatenated file
 * contents) that fits within a configurable character budget. Files are ranked
 * by a keyword match score so the most relevant source files appear first.
 */
@Service
public class ContextExtractorService {

    @Value("${context.char-limit:30000}")
    private int charLimit;

    /**
     * Extracts relevant Java source files from {@code repoPath} and returns
     * them concatenated, each block prefixed with {@code // FILE: <relative-path>}.
     *
     * @param repoPath       root of the cloned repository
     * @param bugDescription free-text bug description used for relevance ranking
     * @return context string capped at {@link #charLimit} characters
     */
    public String extract(Path repoPath, String bugDescription) throws IOException {
        Set<String> keywords = tokenise(bugDescription);

        List<Path> javaFiles = new ArrayList<>();
        try (Stream<Path> walk = Files.walk(repoPath)) {
            walk.filter(Files::isRegularFile)
                .filter(p -> p.toString().endsWith(".java"))
                .filter(p -> !isExcluded(repoPath, p))
                .forEach(javaFiles::add);
        }

        // Sort by descending keyword score, then by path for determinism
        javaFiles.sort(Comparator
                .comparingInt((Path p) -> score(repoPath, p, keywords))
                .reversed()
                .thenComparing(Path::toString));

        StringBuilder sb = new StringBuilder();
        for (Path file : javaFiles) {
            String relativePath = repoPath.relativize(file).toString().replace('\\', '/');
            String content = Files.readString(file);
            String block = "// FILE: " + relativePath + "\n" + content + "\n\n";

            if (sb.length() + block.length() > charLimit) {
                // Try to fit a truncated snippet if the budget is not already exhausted
                int remaining = charLimit - sb.length();
                if (remaining > 64) {
                    sb.append(block, 0, remaining);
                }
                break;
            }
            sb.append(block);
        }

        return sb.toString();
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    /** Tokenises a string into lower-case words (letters/digits only). */
    private Set<String> tokenise(String text) {
        Set<String> tokens = new HashSet<>();
        for (String word : text.split("[^a-zA-Z0-9]+")) {
            if (!word.isEmpty()) tokens.add(word.toLowerCase());
        }
        return tokens;
    }

    /** Counts how many keywords from the bug description appear in the file's relative path. */
    private int score(Path repoPath, Path file, Set<String> keywords) {
        String relPath = repoPath.relativize(file).toString().toLowerCase();
        int count = 0;
        for (String kw : keywords) {
            if (relPath.contains(kw)) count++;
        }
        return count;
    }

    /** Returns true for files that live under build output directories. */
    private boolean isExcluded(Path repoPath, Path file) {
        Path relative = repoPath.relativize(file);
        String first = relative.getNameCount() > 0 ? relative.getName(0).toString() : "";
        return first.equals("target") || first.equals("build") || first.equals(".git");
    }
}
