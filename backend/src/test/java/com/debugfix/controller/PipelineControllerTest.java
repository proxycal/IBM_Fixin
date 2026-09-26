package com.debugfix.controller;

import com.debugfix.dto.*;
import com.debugfix.pipeline.PipelineOrchestrator;
import com.debugfix.service.BuildRunnerService.TestVerificationResult;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class PipelineControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private PipelineOrchestrator orchestrator;

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void health_returns200WithStatusUp() throws Exception {
        mockMvc.perform(get("/api/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
    }

    @Test
    void runPipeline_returns200WithResultDTO() throws Exception {
        PipelineResultDTO dto = new PipelineResultDTO();
        dto.setRootCause("null check missing in UserService.getUser");
        dto.setFixDescription("Added null guard");
        dto.setPatchedFiles(List.of(new PatchedFile(
                "src/main/java/com/example/UserService.java",
                "public class UserService { User getUser(Long id) { if (id == null) return null; } }")));
        dto.setGeneratedTest(new GeneratedTest(
                "src/test/java/com/example/UserServiceTest.java",
                "@Test void getUser_nullId_returnsNull() {}"));
        dto.setTestVerification(new TestVerificationResult(true, "BUILD SUCCESS"));

        when(orchestrator.run(any())).thenReturn(dto);

        PipelineRequestDTO request = new PipelineRequestDTO();
        request.setRepoUrl("https://github.com/example/repo.git");
        request.setBugDescription("NullPointerException in UserService.getUser when id is null");
        request.setBranch("main");

        mockMvc.perform(post("/api/pipeline/run")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rootCause").value("null check missing in UserService.getUser"))
                .andExpect(jsonPath("$.patchedFiles[0].relativePath")
                        .value("src/main/java/com/example/UserService.java"))
                .andExpect(jsonPath("$.testVerification.passed").value(true));
    }

    @Test
    void runPipeline_returns500_onOrchestratorException() throws Exception {
        when(orchestrator.run(any())).thenThrow(new RuntimeException("Failed to clone repo"));

        PipelineRequestDTO request = new PipelineRequestDTO();
        request.setRepoUrl("https://github.com/bad/repo.git");
        request.setBugDescription("some bug");

        mockMvc.perform(post("/api/pipeline/run")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(request)))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.error").exists())
                .andExpect(jsonPath("$.stage").exists());
    }
}
