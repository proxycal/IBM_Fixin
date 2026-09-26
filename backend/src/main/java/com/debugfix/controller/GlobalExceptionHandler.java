package com.debugfix.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;

/**
 * Translates uncaught exceptions from the pipeline into a structured JSON error
 * response so that the React frontend can identify which stage failed.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, String>> handleAll(Exception ex) {
        String message = ex.getMessage() != null ? ex.getMessage() : ex.getClass().getSimpleName();
        // Best-effort stage detection from the exception message
        String stage = detectStage(message);
        return ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("error", message, "stage", stage));
    }

    private String detectStage(String msg) {
        if (msg == null)                    return "unknown";
        if (msg.contains("clone"))          return "clone";
        if (msg.contains("extract"))        return "context-extraction";
        if (msg.contains("root cause"))     return "root-cause-analysis";
        if (msg.contains("fix JSON"))       return "fix-generation";
        if (msg.contains("test JSON"))      return "test-generation";
        if (msg.contains("patch"))          return "patch-apply";
        if (msg.contains("build") || msg.contains("mvn") || msg.contains("gradle"))
                                            return "test-run";
        return "unknown";
    }
}
