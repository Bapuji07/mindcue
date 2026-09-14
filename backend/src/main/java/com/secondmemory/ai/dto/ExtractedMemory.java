package com.secondmemory.ai.dto;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

public record ExtractedMemory(
        String type,
        String title,
        String content,
        BigDecimal importance,
        BigDecimal confidence,
        String resolutionStatus,
        OffsetDateTime occurredAt,
        OffsetDateTime dueAt,
        List<String> sourceChunkIds
) {}
