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
        String errorMessage,
        Instant createdAt,
        Instant updatedAt,
        /* The speaker label in this conversation's transcript that is the person using the app. */
        String selfSpeaker
) {}
