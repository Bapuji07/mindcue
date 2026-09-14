package com.secondmemory.ai.provider;

import com.fasterxml.jackson.databind.JsonNode;
import com.secondmemory.ai.AiCapability;
import com.secondmemory.ai.EmbeddingProvider;
import com.secondmemory.config.AiProperties;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.Map;
import java.util.Set;

@Component
public class OpenAiCompatibleEmbeddingProvider implements EmbeddingProvider {
    private final AiProperties properties;

    public OpenAiCompatibleEmbeddingProvider(AiProperties properties) {
        this.properties = properties;
    }

    @Override
    public String name() {
        return "openai-compatible";
    }

    @Override
    public Set<AiCapability> capabilities() {
        return Set.of(AiCapability.EMBEDDING);
    }

    @Override
    public float[] embed(String text) {
        AiProperties.Embedding embedding = properties.embedding();
        if (embedding.apiKey() == null || embedding.apiKey().isBlank()) {
            throw new IllegalStateException("MEMORY_EMBEDDING_API_KEY is not configured");
        }

        RestClient client = RestClient.builder()
                .baseUrl(embedding.baseUrl())
                .defaultHeader("Authorization", "Bearer " + embedding.apiKey())
                .build();

        JsonNode response = client.post()
                .uri("/embeddings")
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("model", embedding.model(), "input", text))
                .retrieve()
                .body(JsonNode.class);

        if (response == null) {
            throw new IllegalStateException("Embedding provider returned an empty response");
        }
        JsonNode vectorNode = response.at("/data/0/embedding");
        if (!vectorNode.isArray()) {
            throw new IllegalStateException("Embedding provider response did not contain an embedding");
        }

        float[] vector = new float[vectorNode.size()];
        for (int i = 0; i < vectorNode.size(); i++) {
            vector[i] = (float) vectorNode.get(i).asDouble();
        }
        if (vector.length != embedding.dimensions()) {
            throw new IllegalStateException(
                    "Embedding dimension mismatch. Configured " + embedding.dimensions() +
                    " but provider returned " + vector.length);
        }
        return vector;
    }

    @Override
    public int dimensions() {
        return properties.embedding().dimensions();
    }
}
