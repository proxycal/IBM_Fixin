package com.debugfix.service;

import com.debugfix.dto.PatchedFile;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * Writes patched source files returned by the LLM into the cloned repository.
 * The LLM returns full file content (not unified diffs), so each entry is
 * simply written to disk, overwriting the original.
 */
@Service
public class PatchApplierService {

    /**
     * Writes each {@link PatchedFile} to its resolved path under {@code repoPath},
     * creating any missing parent directories.
     *
     * @param repoPath root of the cloned repository
     * @param patches  list of patched files from the LLM response
     */
    public void applyPatch(Path repoPath, List<PatchedFile> patches) throws IOException {
        for (PatchedFile patch : patches) {
            // Normalise separator: LLM output may use '/' regardless of OS
            Path target = repoPath.resolve(Path.of(patch.relativePath()));
            Files.createDirectories(target.getParent());
            Files.writeString(target, patch.content());
        }
    }
}
