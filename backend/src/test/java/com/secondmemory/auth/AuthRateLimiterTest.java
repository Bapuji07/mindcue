package com.secondmemory.auth;

import com.secondmemory.common.LimitExceededException;
import com.secondmemory.config.LimitsProperties;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AuthRateLimiterTest {
    private Instant now = Instant.parse("2026-10-07T10:00:00Z");
    private final Clock clock = new Clock() {
        @Override public ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(ZoneId zone) { return this; }
        @Override public Instant instant() { return now; }
    };
    private final AuthRateLimiter limiter = new AuthRateLimiter(LimitsProperties.defaults(), clock);

    @Test
    void blocksIpAfterTooManyFailuresUntilWindowPasses() {
        for (int i = 0; i < 10; i++) limiter.recordLoginFailure("1.2.3.4");
        assertThrows(LimitExceededException.class, () -> limiter.checkLogin("1.2.3.4"));
        assertDoesNotThrow(() -> limiter.checkLogin("5.6.7.8"));
        now = now.plus(Duration.ofMinutes(16));
        assertDoesNotThrow(() -> limiter.checkLogin("1.2.3.4"));
    }

    @Test
    void successfulLoginClearsFailures() {
        for (int i = 0; i < 10; i++) limiter.recordLoginFailure("1.2.3.4");
        limiter.clearLoginFailures("1.2.3.4");
        assertDoesNotThrow(() -> limiter.checkLogin("1.2.3.4"));
    }

    @Test
    void limitsRegistrationsPerIp() {
        for (int i = 0; i < 5; i++) limiter.checkAndRecordRegistration("1.2.3.4");
        assertThrows(LimitExceededException.class, () -> limiter.checkAndRecordRegistration("1.2.3.4"));
        now = now.plus(Duration.ofMinutes(61));
        assertDoesNotThrow(() -> limiter.checkAndRecordRegistration("1.2.3.4"));
    }
}
