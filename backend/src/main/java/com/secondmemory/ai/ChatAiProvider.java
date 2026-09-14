package com.secondmemory.ai;

public interface ChatAiProvider extends AiProvider {
    String generateText(String systemPrompt, String userPrompt);
    <T> T generateStructured(String systemPrompt, String userPrompt, Class<T> responseType);
}
