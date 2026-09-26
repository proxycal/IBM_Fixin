package com.debugfix.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class ContextExtractorServiceTest {

    private ContextExtractorService service;

    @TempDir
    Path repoRoot;

    @BeforeEach
    void setup() {
        service = new ContextExtractorService();
        // default limit; individual tests may override via ReflectionTestUtils
        ReflectionTestUtils.setField(service, "charLimit", 30_000);
    }

    // -------------------------------------------------------------------------
    // Helper — write a Java file relative to repoRoot
    // -------------------------------------------------------------------------
    private void writeJava(String relativePath, String content) throws IOException {
        Path target = repoRoot.resolve(relativePath);
        Files.createDirectories(target.getParent());
        Files.writeString(target, content);
    }

    // -------------------------------------------------------------------------
    // Tests
    // -------------------------------------------------------------------------

    @Test
    void includesJavaFiles() throws IOException {
        writeJava("src/main/java/com/example/UserService.java",
                "public class UserService {}");
        writeJava("src/main/java/com/example/OrderService.java",
                "public class OrderService {}");

        String ctx = service.extract(repoRoot, "bug in something");

        assertTrue(ctx.contains("UserService.java"), "Should contain UserService.java");
        assertTrue(ctx.contains("OrderService.java"), "Should contain OrderService.java");
    }

    @Test
    void excludesTargetDirectory() throws IOException {
        writeJava("src/main/java/com/example/Main.java", "public class Main {}");
        writeJava("target/classes/com/example/Main.java", "// compiled output");

        String ctx = service.extract(repoRoot, "anything");

        assertFalse(ctx.contains("target/"), "target/ files should be excluded");
        assertTrue(ctx.contains("Main.java"), "src file should still be included");
    }

    @Test
    void keywordRankedFilesAppearFirst() throws IOException {
        writeJava("src/main/java/com/example/UserService.java",
                "public class UserService { void getUser() {} }");
        writeJava("src/main/java/com/example/OrderService.java",
                "public class OrderService {}");

        String ctx = service.extract(repoRoot, "NullPointerException in UserService.getUser");

        int userIdx  = ctx.indexOf("UserService.java");
        int orderIdx = ctx.indexOf("OrderService.java");
        assertTrue(userIdx < orderIdx, "UserService.java should appear before OrderService.java");
    }

    @Test
    void respectsCharLimit() throws IOException {
        // Write a file larger than the tiny limit we set
        String bigContent = "x".repeat(500);
        writeJava("src/main/java/com/example/BigFile.java", bigContent);
        writeJava("src/main/java/com/example/SmallFile.java", "public class SmallFile {}");

        ReflectionTestUtils.setField(service, "charLimit", 100);
        String ctx = service.extract(repoRoot, "anything");

        assertTrue(ctx.length() <= 100, "Output must not exceed the char limit");
    }

    @Test
    void prefixesEachFileBlock() throws IOException {
        writeJava("src/main/java/com/example/Foo.java", "public class Foo {}");

        String ctx = service.extract(repoRoot, "bug");

        assertTrue(ctx.contains("// FILE: src/main/java/com/example/Foo.java"),
                "Each block should be prefixed with // FILE: <relative-path>");
    }

    @Test
    void emptyRepoReturnsEmptyString() throws IOException {
        String ctx = service.extract(repoRoot, "anything");
        assertTrue(ctx.isEmpty(), "Empty repo should produce empty context");
    }
}
