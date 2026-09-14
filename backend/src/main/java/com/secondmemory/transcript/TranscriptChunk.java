package com.secondmemory.transcript;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record TranscriptChunk(
        UUID id,
        UUID sessionId,
        int sequenceNo,
        Long startMs,
        Long endMs,
        String speakerLabel,
        String text,
        BigDecimal confidence,
        Instant createdAt
) {}
