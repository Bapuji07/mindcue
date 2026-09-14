package com.secondmemory.transcription;

import com.secondmemory.ai.dto.ExtractedMemoryResponse;

public record SessionProcessResponse(
        TranscriptionResponse transcription,
        ExtractedMemoryResponse extraction
) {}
