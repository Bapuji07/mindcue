package com.secondmemory.ai.provider;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.secondmemory.ai.AiCapability;
import com.secondmemory.ai.ChatAiProvider;
import com.secondmemory.config.AiProperties;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;
import java.util.Set;

@Component
public class OpenAiCompatibleChatProvider implements ChatAiProvider {
    private final AiProperties properties;
    private final ObjectMapper objectMapper;

    public OpenAiCompatibleChatProvider(AiProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    @Override
    public String name() {
        return "openai-compatible";
    }

    @Override
    public Set<AiCapability> capabilities() {
        return Set.of(AiCapability.CHAT, AiCapability.STRUCTURED_OUTPUT);
    }

    @Override
    public String generateText(String systemPrompt, String userPrompt) {
        AiProperties.Chat chat = properties.chat();
        if (chat.apiKey() == null || chat.apiKey().isBlank()) {
            throw new IllegalStateException("MEMORY_CHAT_API_KEY is not configured");
        }

        RestClient client = RestClient.builder()
                .baseUrl(chat.baseUrl())
                .defaultHeader("Authorization", "Bearer " + chat.apiKey())
                .build();

        Map<String, Object> body = Map.of(
                "model", chat.model(),
                "temperature", chat.temperature(),
                "messages", List.of(
                        Map.of("role", "system", "content", systemPrompt),
                        Map.of("role", "user", "content", userPrompt)
                )
        );

        JsonNode response = client.post()
                .uri("/chat/completions")
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .body(JsonNode.class);

        if (response == null) {
            throw new IllegalStateException("AI provider returned an empty response");
        }
        JsonNode content = response.at("/choices/0/message/content");
        if (content.isMissingNode() || content.asText().isBlank()) {
            throw new IllegalStateException("AI provider response did not contain message content");
        }
        return content.asText();
    }

    @Override
    public <T> T generateStructured(String systemPrompt, String userPrompt, Class<T> responseType) {
        String jsonInstruction = "\nReturn ONLY valid JSON matching the requested schema. Do not use markdown fences.";
        String raw = generateText(systemPrompt + jsonInstruction, userPrompt);
        String cleaned = stripMarkdownFence(raw);
        try {
            return objectMapper.readValue(cleaned, responseType);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Could not parse structured AI response: " + cleaned, e);
        }
    }

    private String stripMarkdownFence(String value) {
        String trimmed = value.trim();
        if (trimmed.startsWith("```")) {
            int firstLine = trimmed.indexOf('\n');
            int lastFence = trimmed.lastIndexOf("```");
            if (firstLine > -1 && lastFence > firstLine) {
                return trimmed.substring(firstLine + 1, lastFence).trim();
            }
        }
        return trimmed;
    }
}
