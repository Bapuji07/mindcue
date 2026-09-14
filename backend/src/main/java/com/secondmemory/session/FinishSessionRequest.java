package com.secondmemory.session;

import java.time.OffsetDateTime;

public record FinishSessionRequest(OffsetDateTime endedAt, Integer durationSeconds) {}
