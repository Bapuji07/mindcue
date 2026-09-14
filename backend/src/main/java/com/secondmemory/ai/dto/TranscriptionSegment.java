package com.secondmemory.ai.dto;

import java.math.BigDecimal;

public record TranscriptionSegment(
        Long startMs,
        Long endMs,
        String speakerLabel,
        String text,
        BigDecimal confidence
) {}
