package com.secondmemory.memory;

import java.util.UUID;

public record MemorySourceEvidence(
        UUID memoryId,
        UUID sessionId,
        UUID transcriptChunkId,
        Integer sequenceNo,
        String speakerLabel,
        String text
) {}
