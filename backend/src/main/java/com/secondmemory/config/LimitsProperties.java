package com.secondmemory.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Usage limits that keep one shared AI key affordable. Any value left unset falls back to the
 * default below, so a config file without a memory.limits block still runs with sane limits.
 */
@ConfigurationProperties(prefix = "memory.limits")
public record LimitsProperties(
        Integer monthlyAudioMinutes,
        Integer maxRecordingMinutes,
        Integer dailyAiRequests,
        Integer maxProcessingAttempts,
        Integer globalDailyAudioMinutes,
        Integer loginFailuresPerIp,
        Integer loginWindowMinutes,
        Integer registrationsPerIp,
        Integer registrationWindowMinutes
) {
    public LimitsProperties {
        monthlyAudioMinutes = orDefault(monthlyAudioMinutes, 120);
        maxRecordingMinutes = orDefault(maxRecordingMinutes, 60);
        dailyAiRequests = orDefault(dailyAiRequests, 50);
        maxProcessingAttempts = orDefault(maxProcessingAttempts, 3);
        globalDailyAudioMinutes = orDefault(globalDailyAudioMinutes, 600);
        loginFailuresPerIp = orDefault(loginFailuresPerIp, 10);
        loginWindowMinutes = orDefault(loginWindowMinutes, 15);
        registrationsPerIp = orDefault(registrationsPerIp, 5);
        registrationWindowMinutes = orDefault(registrationWindowMinutes, 60);
    }

    public static LimitsProperties defaults() {
        return new LimitsProperties(null, null, null, null, null, null, null, null, null);
    }

    private static Integer orDefault(Integer value, int fallback) {
        return value == null || value <= 0 ? fallback : value;
    }
}
