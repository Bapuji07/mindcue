package com.secondmemory.memory;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record MemoryRecord(
        UUID id,
        UUID userId,
        UUID sessionId,
        MemoryType type,
        String title,
        String content,
        BigDecimal importance,
        BigDecimal confidence,
        ResolutionStatus resolutionStatus,
        Instant occurredAt,
        Instant dueAt,
        String aiProvider,
        String aiModel,
        String promptVersion,
        boolean active,
        Instant createdAt,
        Instant updatedAt
) {}
