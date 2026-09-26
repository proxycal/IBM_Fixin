package com.debugfix.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Binds {@code watsonx.*} properties from {@code application.properties}.
 * Used for the Groq API key and model selection.
 */
@Configuration
@ConfigurationProperties(prefix = "watsonx")
public class WatsonxConfig {

    private String apiKey;
    private String modelId = "llama-3.3-70b-versatile";

    public String getApiKey()          { return apiKey; }
    public void   setApiKey(String v)  { this.apiKey = v; }

    public String getModelId()         { return modelId; }
    public void   setModelId(String v) { this.modelId = v; }
}
