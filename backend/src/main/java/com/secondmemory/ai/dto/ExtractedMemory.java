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
        /* Chunk numbers (or ids) of the transcript chunks that support the memory. */
        List<String> sourceChunkIds,
        /* Who must act on it or made the promise, as the transcript names them; null if unclear. */
        String owner
) {}
