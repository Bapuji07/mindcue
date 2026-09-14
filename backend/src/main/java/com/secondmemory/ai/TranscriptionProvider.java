package com.secondmemory.ai;

import com.secondmemory.ai.dto.TranscriptionResult;

import java.nio.file.Path;

public interface TranscriptionProvider extends AiProvider {
    TranscriptionResult transcribe(Path audioFile);
}
