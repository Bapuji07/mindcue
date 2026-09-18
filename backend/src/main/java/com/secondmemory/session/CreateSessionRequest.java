package com.secondmemory.session;

import jakarta.validation.constraints.NotNull;

import java.time.OffsetDateTime;

public record CreateSessionRequest(
        String title,
        @NotNull OffsetDateTime startedAt,
        String timezone,
        String source
) {}
