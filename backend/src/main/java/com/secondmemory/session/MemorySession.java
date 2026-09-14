package com.secondmemory.session;

import java.time.Instant;
import java.util.UUID;

public record MemorySession(
        UUID id,
        UUID userId,
        String title,
        SessionStatus status,
        String source,
        Instant startedAt,
        Instant endedAt,
        String timezone,
        String audioUri,
        Integer durationSeconds,
        String summary,
        Instant createdAt,
        Instant updatedAt
) {}
