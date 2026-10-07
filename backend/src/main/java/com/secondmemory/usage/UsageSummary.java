package com.secondmemory.usage;

import java.time.Instant;

public record UsageSummary(
        boolean unlimited,
        int audioMinutesUsed,
        int audioMinutesLimit,
        Instant audioResetsAt,
        int aiRequestsToday,
        int aiRequestsLimit,
        Instant aiResetsAt,
        int maxRecordingMinutes
) {}
