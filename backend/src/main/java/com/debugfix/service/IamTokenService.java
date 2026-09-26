package com.debugfix.service;

import com.debugfix.config.WatsonxConfig;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Instant;

/**
 * Exchanges the watsonx.ai API key for a short-lived IBM IAM Bearer token and
 * caches it until it expires.
 *
 * <p>Token endpoint: {@code POST https://iam.cloud.ibm.com/identity/token}
 * body (form-encoded): {@code grant_type=urn:ibm:params:oauth:grant-type:apikey&apikey=<key>}
 */
@Service
public class IamTokenService {

    static final String IAM_URL = "https://iam.cloud.ibm.com/identity/token";

    private final WatsonxConfig config;
    private final WebClient webClient;
    private final ObjectMapper mapper = new ObjectMapper();

    private volatile String cachedToken;
    private volatile Instant expiresAt = Instant.MIN;

    public IamTokenService(WatsonxConfig config, WebClient.Builder webClientBuilder) {
        this.config    = config;
        this.webClient = webClientBuilder.build();
    }

    /**
     * Returns a valid Bearer token, refreshing from IAM if the cached one has expired.
     */
    public synchronized String getBearerToken() {
        if (cachedToken == null || Instant.now().isAfter(expiresAt)) {
            refresh();
        }
        return cachedToken;
    }

    // -------------------------------------------------------------------------
    // Package-private for testing (allows injecting a different IAM base URL)
    // -------------------------------------------------------------------------
    String iamUrl = IAM_URL;

    private void refresh() {
        String body = "grant_type=urn:ibm:params:oauth:grant-type:apikey&apikey=" + config.getApiKey();

        String json = webClient.post()
                .uri(iamUrl)
                .header("Content-Type", "application/x-www-form-urlencoded")
                .body(BodyInserters.fromValue(body))
                .retrieve()
                .bodyToMono(String.class)
                .block();

        try {
            JsonNode node  = mapper.readTree(json);
            cachedToken    = node.get("access_token").asText();
            long expiresIn = node.get("expires_in").asLong(3600);
            // Refresh 60 seconds before actual expiry to avoid edge-case races
            expiresAt = Instant.now().plusSeconds(expiresIn - 60);
        } catch (Exception e) {
            throw new RuntimeException("Failed to parse IAM token response: " + json, e);
        }
    }
}
