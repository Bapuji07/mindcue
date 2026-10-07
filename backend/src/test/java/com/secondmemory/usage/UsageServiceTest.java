package com.secondmemory.usage;

import com.secondmemory.common.LimitExceededException;
import com.secondmemory.config.LimitsProperties;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class UsageServiceTest {
    private static final Instant NOW = Instant.parse("2026-10-07T15:30:00Z");
    private static final Instant MONTH_START = Instant.parse("2026-10-01T00:00:00Z");
    private static final Instant DAY_START = Instant.parse("2026-10-07T00:00:00Z");

    private final UUID userId = UUID.randomUUID();
    private final UUID sessionId = UUID.randomUUID();
    private final UsageRepository repository = mock(UsageRepository.class);
    private final UsageService service = new UsageService(repository, LimitsProperties.defaults(),
            Clock.fixed(NOW, ZoneOffset.UTC));

    @Test
    void chargesAudioWithinMonthlyLimit() {
        when(repository.sumSince(userId, UsageKind.AUDIO_SECONDS, MONTH_START)).thenReturn(60L * 60);
        service.chargeAudio(userId, sessionId, 30 * 60);
        verify(repository).insert(userId, sessionId, UsageKind.AUDIO_SECONDS, 30 * 60);
    }

    @Test
    void rejectsAudioOverMonthlyLimitWithResetDate() {
        when(repository.sumSince(userId, UsageKind.AUDIO_SECONDS, MONTH_START)).thenReturn(115L * 60);
        var ex = assertThrows(LimitExceededException.class, () -> service.chargeAudio(userId, sessionId, 10 * 60));
        assertThat(ex.getMessage()).contains("5 min", "1 Nov");
        assertEquals(Instant.parse("2026-11-01T00:00:00Z"), ex.resetsAt());
        verify(repository, never()).insert(any(), any(), any(), anyInt());
    }

    @Test
    void secondChargeForSameSessionIsFree() {
        when(repository.hasAudioCharge(sessionId)).thenReturn(true);
        when(repository.sumSince(userId, UsageKind.AUDIO_SECONDS, MONTH_START)).thenReturn(999L * 60);
        service.chargeAudio(userId, sessionId, 10 * 60);
        verify(repository, never()).insert(any(), any(), any(), anyInt());
    }

    @Test
    void unlimitedUserSkipsMonthlyLimitButNotGlobalCap() {
        when(repository.lockUserAndCheckUnlimited(userId)).thenReturn(true);
        when(repository.sumSince(userId, UsageKind.AUDIO_SECONDS, MONTH_START)).thenReturn(999L * 60);
        service.chargeAudio(userId, sessionId, 10 * 60);
        verify(repository).insert(userId, sessionId, UsageKind.AUDIO_SECONDS, 10 * 60);

        UUID other = UUID.randomUUID();
        when(repository.globalSumSince(UsageKind.AUDIO_SECONDS, DAY_START)).thenReturn(595L * 60);
        assertThrows(LimitExceededException.class, () -> service.chargeAudio(userId, other, 10 * 60));
    }

    @Test
    void dailyAiLimitIsEnforcedAndReleaseGivesItBack() {
        when(repository.sumSince(userId, UsageKind.AI_REQUEST, DAY_START)).thenReturn(49L);
        UUID reservation = UUID.randomUUID();
        when(repository.insert(userId, null, UsageKind.AI_REQUEST, 1)).thenReturn(reservation);
        assertEquals(reservation, service.reserveAiRequest(userId));
        service.releaseAiRequest(reservation);
        verify(repository).delete(reservation);

        when(repository.sumSince(userId, UsageKind.AI_REQUEST, DAY_START)).thenReturn(50L);
        assertThrows(LimitExceededException.class, () -> service.reserveAiRequest(userId));
    }

    @Test
    void tooLongRecordingIsRejectedAtUpload() {
        var ex = assertThrows(ResponseStatusException.class, () -> service.checkRecordingAllowed(userId, 61 * 60));
        assertEquals(HttpStatus.PAYLOAD_TOO_LARGE, ex.getStatusCode());
    }

    @Test
    void summaryRoundsMinutesUp() {
        when(repository.sumSince(eq(userId), eq(UsageKind.AUDIO_SECONDS), any())).thenReturn(61L);
        when(repository.sumSince(eq(userId), eq(UsageKind.AI_REQUEST), any())).thenReturn(4L);
        UsageSummary summary = service.summary(userId);
        assertEquals(2, summary.audioMinutesUsed());
        assertEquals(4, summary.aiRequestsToday());
        assertEquals(Instant.parse("2026-11-01T00:00:00Z"), summary.audioResetsAt());
        assertEquals(Instant.parse("2026-10-08T00:00:00Z"), summary.aiResetsAt());
    }
}
