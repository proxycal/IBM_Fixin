package com.debugfix.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Runs the test suite inside a cloned repository by invoking the appropriate
 * build tool ({@code mvn test} or {@code ./gradlew test}) as a child process.
 */
@Service
public class BuildRunnerService {

    @Value("${build.timeout-minutes:5}")
    private int timeoutMinutes;

    // -------------------------------------------------------------------------
    // Public result type
    // -------------------------------------------------------------------------

    /**
     * Outcome of running the test suite.
     *
     * @param passed {@code true} if the process exited with code 0
     * @param output combined stdout + stderr from the build tool
     */
    public record TestVerificationResult(boolean passed, String output) {}

    // -------------------------------------------------------------------------
    // Public API
    // -------------------------------------------------------------------------

    /**
     * Detects the build tool in {@code repoPath}, runs its test command, and
     * returns the outcome.
     *
     * @throws UnsupportedBuildToolException if neither {@code pom.xml} nor a
     *                                        Gradle build file is found
     */
    public TestVerificationResult runTests(Path repoPath) throws IOException, InterruptedException {
        List<String> command = buildCommand(repoPath);

        ProcessBuilder pb = new ProcessBuilder(command);
        pb.directory(repoPath.toFile());
        pb.redirectErrorStream(true);   // merge stderr → stdout

        Process process = pb.start();

        // Drain stdout in the same thread — the process buffer would deadlock
        // if we called waitFor() before reading output on a long build.
        StringBuilder output = new StringBuilder();
        try (BufferedReader reader =
                     new BufferedReader(new InputStreamReader(process.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                output.append(line).append(System.lineSeparator());
            }
        }

        boolean finished = process.waitFor(timeoutMinutes, TimeUnit.MINUTES);
        if (!finished) {
            process.destroyForcibly();
            output.append("[BUILD RUNNER] Process timed out after ")
                  .append(timeoutMinutes).append(" minute(s) and was killed.");
            return new TestVerificationResult(false, output.toString());
        }

        return new TestVerificationResult(process.exitValue() == 0, output.toString());
    }

    // -------------------------------------------------------------------------
    // Detection logic (package-private for unit testing)
    // -------------------------------------------------------------------------

    List<String> buildCommand(Path repoPath) {
        if (Files.exists(repoPath.resolve("pom.xml"))) {
            return mavenCommand();
        }
        if (Files.exists(repoPath.resolve("build.gradle"))
                || Files.exists(repoPath.resolve("build.gradle.kts"))) {
            return gradleCommand(repoPath);
        }
        throw new UnsupportedBuildToolException(
                "No pom.xml or build.gradle found in: " + repoPath);
    }

    private List<String> mavenCommand() {
        boolean isWindows = System.getProperty("os.name", "").toLowerCase().contains("win");
        String mvn = isWindows ? "mvn.cmd" : "mvn";
        return List.of(mvn, "test", "-B");
    }

    private List<String> gradleCommand(Path repoPath) {
        boolean isWindows = System.getProperty("os.name", "").toLowerCase().contains("win");
        // Use the wrapper if it exists; fall back to system Gradle
        if (Files.exists(repoPath.resolve(isWindows ? "gradlew.bat" : "gradlew"))) {
            String wrapper = isWindows ? "gradlew.bat" : "./gradlew";
            return List.of(wrapper, "test");
        }
        return List.of("gradle", "test");
    }

    // -------------------------------------------------------------------------
    // Exception
    // -------------------------------------------------------------------------

    public static class UnsupportedBuildToolException extends RuntimeException {
        public UnsupportedBuildToolException(String message) { super(message); }
    }
}
