package com.debugfix.service;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class GitServiceTest {

    @Autowired
    private GitService gitService;

    @Test
    void cloneAndDeleteRepo() throws Exception {
        Path cloned = gitService.cloneRepo("https://github.com/octocat/Hello-World.git", "master");

        assertTrue(Files.exists(cloned), "Cloned directory should exist");
        assertTrue(Files.isDirectory(cloned), "Cloned path should be a directory");
        assertTrue(Files.isDirectory(cloned.resolve(".git")), ".git directory should exist inside the clone");

        gitService.deleteRepo(cloned);

        assertFalse(Files.exists(cloned), "Cloned directory should be deleted after deleteRepo()");
    }
}
