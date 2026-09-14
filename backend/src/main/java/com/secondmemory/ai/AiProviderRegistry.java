package com.secondmemory.ai;

import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
public class AiProviderRegistry {
    private final Map<String, ChatAiProvider> chatProviders;
    private final Map<String, EmbeddingProvider> embeddingProviders;
    private final Map<String, TranscriptionProvider> transcriptionProviders;

    public AiProviderRegistry(List<ChatAiProvider> chatProviders,
                              List<EmbeddingProvider> embeddingProviders,
                              List<TranscriptionProvider> transcriptionProviders) {
        this.chatProviders = chatProviders.stream()
                .collect(Collectors.toUnmodifiableMap(ChatAiProvider::name, Function.identity()));
        this.embeddingProviders = embeddingProviders.stream()
                .collect(Collectors.toUnmodifiableMap(EmbeddingProvider::name, Function.identity()));
        this.transcriptionProviders = transcriptionProviders.stream()
                .collect(Collectors.toUnmodifiableMap(TranscriptionProvider::name, Function.identity()));
    }

    public ChatAiProvider chat(String providerName) {
        ChatAiProvider provider = chatProviders.get(providerName);
        if (provider == null) {
            throw new IllegalArgumentException("Unsupported chat AI provider: " + providerName);
        }
        return provider;
    }

    public EmbeddingProvider embedding(String providerName) {
        EmbeddingProvider provider = embeddingProviders.get(providerName);
        if (provider == null) {
            throw new IllegalArgumentException("Unsupported embedding AI provider: " + providerName);
        }
        return provider;
    }

    public TranscriptionProvider transcription(String providerName) {
        TranscriptionProvider provider = transcriptionProviders.get(providerName);
        if (provider == null) {
            throw new IllegalArgumentException("Unsupported transcription AI provider: " + providerName);
        }
        return provider;
    }
}
