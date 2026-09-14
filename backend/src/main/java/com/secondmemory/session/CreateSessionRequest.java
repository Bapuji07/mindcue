package com.secondmemory.session;

import jakarta.validation.constraints.NotNull;

import java.time.OffsetDateTime;
import java.util.UUID;

public record CreateSessionRequest(
        @NotNull UUID userId,
        String title,
        @NotNull OffsetDateTime startedAt,
        String timezone,
        String source
) {}
