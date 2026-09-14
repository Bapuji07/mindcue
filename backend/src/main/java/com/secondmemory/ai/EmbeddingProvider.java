package com.secondmemory.ai;

public interface EmbeddingProvider extends AiProvider {
    float[] embed(String text);
    int dimensions();
}
