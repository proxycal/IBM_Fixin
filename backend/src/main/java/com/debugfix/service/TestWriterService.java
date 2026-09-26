package com.debugfix.service;

import com.debugfix.dto.GeneratedTest;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Writes an LLM-generated JUnit regression test file into the cloned repository.
 */
@Service
public class TestWriterService {

    /**
     * Writes the test file to its resolved path under {@code repoPath},
     * creating any missing parent directories.
     *
     * @param repoPath      root of the cloned repository
     * @param generatedTest the test file returned by the LLM
     */
    public void writeTest(Path repoPath, GeneratedTest generatedTest) throws IOException {
        Path target = repoPath.resolve(Path.of(generatedTest.relativePath()));
        Files.createDirectories(target.getParent());
        Files.writeString(target, generatedTest.content());
    }
}
