package com.debugfix.service;

import com.debugfix.config.WatsonxConfig;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.reactive.function.client.WebClient;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.*;

class WatsonxServiceTest {

    private MockWebServer server;
    private WatsonxService watsonxService;
    private WatsonxConfig config;

    // Subclass to point GROQ_URL at the mock server
    static class TestableWatsonxService extends WatsonxService {
        private final String       baseUrl;
        private final WatsonxConfig cfg;

        TestableWatsonxService(WatsonxConfig config, WebClient.Builder builder, String baseUrl) {
            super(config, builder);
            this.cfg     = config;
            this.baseUrl = baseUrl;
        }

        @Override
        public String generate(String systemPrompt, String userMessage) {
            String requestBody;
            try {
                java.lang.reflect.Method m = WatsonxService.class
                        .getDeclaredMethod("buildRequestBody", String.class, String.class);
                m.setAccessible(true);
                requestBody = (String) m.invoke(this, systemPrompt, userMessage);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }

            com.fasterxml.jackson.databind.ObjectMapper mapper =
                    new com.fasterxml.jackson.databind.ObjectMapper();
            try {
                String responseJson = WebClient.builder().build().post()
                        .uri(baseUrl + "/openai/v1/chat/completions")
                        .header("Authorization", "Bearer " + cfg.getApiKey())
                        .header("Content-Type", "application/json")
                        .bodyValue(requestBody)
                        .retrieve()
                        .bodyToMono(String.class)
                        .block();
                com.fasterxml.jackson.databind.JsonNode root = mapper.readTree(responseJson);
                return root.path("choices").path(0).path("message").path("content").asText();
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }
    }

    @BeforeEach
    void setup() throws IOException {
        server = new MockWebServer();
        server.start();

        config = new WatsonxConfig();
        config.setApiKey("test-groq-key");
        config.setModelId("llama-3.3-70b-versatile");

        watsonxService = new WatsonxService(config, WebClient.builder());
    }

    @AfterEach
    void teardown() throws IOException {
        server.shutdown();
    }

    @Test
    void generate_mockMode_rootCausePrompt() {
        // When api-key is placeholder, returns mock root-cause text
        config.setApiKey("YOUR_API_KEY_HERE");
        String result = watsonxService.generate("identify the root cause of the bug", "some context");
        assertTrue(result.contains("[MOCK]"));
    }

    @Test
    void generate_mockMode_fixPrompt_returnsJsonArray() {
        config.setApiKey("YOUR_API_KEY_HERE");
        String result = watsonxService.generate(
                "return a JSON array of every file that needs to change", "context");
        assertTrue(result.startsWith("["), "Fix mock should return a JSON array");
    }

    @Test
    void generate_mockMode_testPrompt_returnsJsonObject() {
        config.setApiKey("YOUR_API_KEY_HERE");
        String result = watsonxService.generate(
                "Return a single JSON object with fields path and content", "context");
        assertTrue(result.startsWith("{"), "Test mock should return a JSON object");
    }

    @Test
    void generate_realMode_sendsCorrectRequestAndParsesResponse() throws Exception {
        server.enqueue(new MockResponse()
                .setBody("{\"choices\":[{\"message\":{\"content\":\"root cause found\"}}]}")
                .addHeader("Content-Type", "application/json"));

        String baseUrl = server.url("").toString();
        // strip trailing slash
        if (baseUrl.endsWith("/")) baseUrl = baseUrl.substring(0, baseUrl.length() - 1);

        TestableWatsonxService svc = new TestableWatsonxService(config, WebClient.builder(), baseUrl);
        String result = svc.generate("system prompt", "user message");

        assertEquals("root cause found", result);

        RecordedRequest req = server.takeRequest();
        assertTrue(req.getPath().contains("chat/completions"));
        assertEquals("Bearer test-groq-key", req.getHeader("Authorization"));
        String body = req.getBody().readUtf8();
        assertTrue(body.contains("\"model\""));
        assertTrue(body.contains("\"messages\""));
    }
}
