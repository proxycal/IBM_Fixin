package com.debugfix.dto;

/**
 * A single file whose content has been replaced by the LLM fix proposal.
 *
 * @param relativePath path relative to the repository root (forward slashes)
 * @param content      full corrected file content
 */
public record PatchedFile(String relativePath, String content) {}
