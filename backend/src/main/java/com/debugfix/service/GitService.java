package com.debugfix.service;

import org.eclipse.jgit.api.Git;
import org.springframework.stereotype.Service;
import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;

@Service
public class GitService {

    /**
     * Clones the given remote Git repository URL (public) at the specified branch
     * into a fresh temp directory. Returns the path to the cloned directory.
     */
    public Path cloneRepo(String repoUrl, String branch) throws Exception {
        Path tempDir = Files.createTempDirectory("debugfix-");
        Git.cloneRepository()
                .setURI(repoUrl)
                .setBranch(branch)
                .setDirectory(tempDir.toFile())
                .call()
                .close();
        return tempDir;
    }

    /**
     * Recursively deletes the given directory. Safe to call even if the path
     * no longer exists. Used in finally-blocks to clean up temp clones.
     */
    public void deleteRepo(Path repoPath) throws IOException {
        if (!Files.exists(repoPath)) return;
        Files.walkFileTree(repoPath, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                file.toFile().setWritable(true);
                Files.delete(file);
                return FileVisitResult.CONTINUE;
            }
            @Override
            public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
                dir.toFile().setWritable(true);
                Files.delete(dir);
                return FileVisitResult.CONTINUE;
            }
        });
    }
}
