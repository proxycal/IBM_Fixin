package com.debugfix.service;

import com.debugfix.dto.GeneratedTest;
import com.debugfix.dto.PatchedFile;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class PatchApplierAndTestWriterServiceTest {

    private PatchApplierService patchApplier;
    private TestWriterService   testWriter;

    @TempDir
    Path repoRoot;

    @BeforeEach
    void setup() {
        patchApplier = new PatchApplierService();
        testWriter   = new TestWriterService();
    }

    // -------------------------------------------------------------------------
    // PatchApplierService
    // -------------------------------------------------------------------------

    @Test
    void applyPatch_writesFileWithCorrectContent() throws Exception {
        String relativePath = "src/main/java/com/example/UserService.java";
        String content      = "public class UserService { void fix() {} }";

        patchApplier.applyPatch(repoRoot, List.of(new PatchedFile(relativePath, content)));

        Path written = repoRoot.resolve(relativePath);
        assertTrue(Files.exists(written), "Patched file should exist");
        assertEquals(content, Files.readString(written));
    }

    @Test
    void applyPatch_createsParentDirectories() throws Exception {
        String deep = "a/b/c/d/Deep.java";
        patchApplier.applyPatch(repoRoot, List.of(new PatchedFile(deep, "class Deep {}")));

        assertTrue(Files.exists(repoRoot.resolve(deep)));
    }

    @Test
    void applyPatch_handlesMultipleFiles() throws Exception {
        List<PatchedFile> patches = List.of(
                new PatchedFile("src/main/java/com/example/A.java", "class A {}"),
                new PatchedFile("src/main/java/com/example/B.java", "class B {}")
        );
        patchApplier.applyPatch(repoRoot, patches);

        assertTrue(Files.exists(repoRoot.resolve("src/main/java/com/example/A.java")));
        assertTrue(Files.exists(repoRoot.resolve("src/main/java/com/example/B.java")));
    }

    @Test
    void applyPatch_overwritesExistingFile() throws Exception {
        String path = "src/main/java/com/example/Foo.java";
        Path target = repoRoot.resolve(path);
        Files.createDirectories(target.getParent());
        Files.writeString(target, "old content");

        patchApplier.applyPatch(repoRoot, List.of(new PatchedFile(path, "new content")));

        assertEquals("new content", Files.readString(target));
    }

    // -------------------------------------------------------------------------
    // TestWriterService
    // -------------------------------------------------------------------------

    @Test
    void writeTest_writesFileAtCorrectPath() throws Exception {
        String path    = "src/test/java/com/example/UserServiceTest.java";
        String content = "@Test void regressionTest() {}";

        testWriter.writeTest(repoRoot, new GeneratedTest(path, content));

        Path written = repoRoot.resolve(path);
        assertTrue(Files.exists(written));
        assertEquals(content, Files.readString(written));
    }

    @Test
    void writeTest_createsParentDirectories() throws Exception {
        String path = "src/test/java/com/very/deep/FooTest.java";
        testWriter.writeTest(repoRoot, new GeneratedTest(path, "class FooTest {}"));

        assertTrue(Files.exists(repoRoot.resolve(path)));
    }
}
