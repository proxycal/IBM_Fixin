package com.debugfix.service;

import com.debugfix.config.WatsonxConfig;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

/**
 * Calls the Groq chat-completions API (OpenAI-compatible).
 *
 * <p>Endpoint: {@code POST https://api.groq.com/openai/v1/chat/completions}
 *
 * <p>When {@code watsonx.api-key} is the placeholder {@code YOUR_API_KEY_HERE},
 * mock responses are returned so the pipeline can be tested without credentials.
 */
@Service
public class WatsonxService {

    private static final String PLACEHOLDER  = "YOUR_API_KEY_HERE";
    private static final String GROQ_URL     = "https://api.groq.com/openai/v1/chat/completions";
    private static final String DEFAULT_MODEL = "llama-3.3-70b-versatile";

    private final WatsonxConfig config;
    private final WebClient     webClient;
    private final ObjectMapper  mapper = new ObjectMapper();

    public WatsonxService(WatsonxConfig config, WebClient.Builder webClientBuilder) {
        this.config    = config;
        this.webClient = webClientBuilder.build();
    }

    /**
     * Sends a chat request to Groq and returns the assistant's reply text.
     * Falls back to mock responses when the API key is the placeholder.
     *
     * @param systemPrompt the system-role instruction
     * @param userMessage  the user-role message
     * @return generated text
     */
    public String generate(String systemPrompt, String userMessage) {
        if (PLACEHOLDER.equals(config.getApiKey())) {
            return mockResponse(systemPrompt);
        }

        String requestBody = buildRequestBody(systemPrompt, userMessage);
        String apiKey      = config.getApiKey();

        String responseJson = webClient.post()
                .uri(java.net.URI.create(GROQ_URL))
                .header("Authorization", "Bearer " + apiKey)
                .header("Content-Type", "application/json")
                .bodyValue(requestBody)
                .retrieve()
                .bodyToMono(String.class)
                .block();

        return parseResponse(responseJson);
    }

    // -------------------------------------------------------------------------
    // Request / response helpers
    // -------------------------------------------------------------------------

    private String buildRequestBody(String systemPrompt, String userMessage) {
        try {
            String model = (config.getModelId() != null && !config.getModelId().isBlank())
                    ? config.getModelId() : DEFAULT_MODEL;

            ObjectNode systemMsg = mapper.createObjectNode();
            systemMsg.put("role",    "system");
            systemMsg.put("content", systemPrompt);

            ObjectNode userMsg = mapper.createObjectNode();
            userMsg.put("role",    "user");
            userMsg.put("content", userMessage);

            ArrayNode messages = mapper.createArrayNode();
            messages.add(systemMsg);
            messages.add(userMsg);

            ObjectNode root = mapper.createObjectNode();
            root.put("model",      model);
            root.set("messages",   messages);
            root.put("max_tokens", 4096);
            root.put("temperature", 0.2);

            return mapper.writeValueAsString(root);
        } catch (Exception e) {
            throw new RuntimeException("Failed to build Groq request body", e);
        }
    }

    private String parseResponse(String responseJson) {
        try {
            JsonNode root    = mapper.readTree(responseJson);
            JsonNode message = root.path("choices").path(0).path("message");
            String content   = message.path("content").asText("").trim();
            // Reasoning models (openai/gpt-oss-*) put output in "reasoning" when content is empty
            if (content.isEmpty()) {
                content = message.path("reasoning").asText("").trim();
            }
            return content;
        } catch (Exception e) {
            throw new RuntimeException("Failed to parse Groq response: " + responseJson, e);
        }
    }

    // -------------------------------------------------------------------------
    // Mock responses (used when api-key is the placeholder)
    // -------------------------------------------------------------------------

    private String mockResponse(String systemPrompt) {
        String lc = systemPrompt.toLowerCase();
        if (lc.contains("json array")) {
            return "[{\"path\":\"src/main/java/com/example/Example.java\"," +
                   "\"content\":\"package com.example;\\n\\npublic class Example {\\n" +
                   "    public String process(String input) {\\n" +
                   "        if (input == null) return null;\\n" +
                   "        return input.trim();\\n" +
                   "    }\\n}\\n\"}]";
        }
        if (lc.contains("json object")) {
            return "{\"path\":\"src/test/java/com/example/ExampleTest.java\"," +
                   "\"content\":\"package com.example;\\n\\nimport org.junit.jupiter.api.Test;\\n" +
                   "import static org.junit.jupiter.api.Assertions.*;\\n\\n" +
                   "class ExampleTest {\\n" +
                   "    @Test void process_nullInput_returnsNull() {\\n" +
                   "        assertNull(new Example().process(null));\\n" +
                   "    }\\n}\\n\"}";
        }
        return "[MOCK] The root cause is a missing null-check in the target method. " +
               "The code does not guard against a null input parameter before dereferencing it, " +
               "causing a NullPointerException at runtime.";
    }
}
