package com.secondmemory.transcript;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PositiveOrZero;

import java.math.BigDecimal;

public record CreateTranscriptChunkRequest(
        @PositiveOrZero int sequenceNo,
        Long startMs,
        Long endMs,
        String speakerLabel,
        @NotBlank String text,
        BigDecimal confidence
) {}
