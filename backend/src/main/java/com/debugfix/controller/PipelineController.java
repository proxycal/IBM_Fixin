package com.debugfix.controller;

import com.debugfix.dto.PipelineRequestDTO;
import com.debugfix.dto.PipelineResultDTO;
import com.debugfix.pipeline.PipelineOrchestrator;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * Exposes the debug-to-fix pipeline over HTTP.
 *
 * <ul>
 *   <li>{@code POST /api/pipeline/run} — triggers the full five-stage pipeline.</li>
 *   <li>{@code GET  /api/health}        — simple liveness check.</li>
 * </ul>
 */
@RestController
@RequestMapping("/api")
public class PipelineController {

    private final PipelineOrchestrator orchestrator;

    public PipelineController(PipelineOrchestrator orchestrator) {
        this.orchestrator = orchestrator;
    }

    @PostMapping("/pipeline/run")
    public ResponseEntity<PipelineResultDTO> runPipeline(@RequestBody PipelineRequestDTO request)
            throws Exception {
        PipelineResultDTO result = orchestrator.run(request);
        return ResponseEntity.ok(result);
    }

    @GetMapping("/health")
    public ResponseEntity<Map<String, String>> health() {
        return ResponseEntity.ok(Map.of("status", "UP"));
    }
}
