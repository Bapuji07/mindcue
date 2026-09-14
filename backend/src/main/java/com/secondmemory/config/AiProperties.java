package com.secondmemory.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "memory.ai")
public record AiProperties(Chat chat, Embedding embedding, Transcription transcription) {
    public record Chat(String provider, String baseUrl, String apiKey, String model, double temperature) {}
    public record Embedding(boolean enabled, String provider, String baseUrl, String apiKey, String model, int dimensions) {}
    public record Transcription(String provider, String baseUrl, String apiKey, String model, String language) {}
}
