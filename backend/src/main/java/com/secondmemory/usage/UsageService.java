package com.secondmemory.usage;

import com.secondmemory.common.LimitExceededException;
import com.secondmemory.config.LimitsProperties;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.UUID;

/**
 * Meters the shared AI key per user. Month and day windows are calendar UTC. Per-user checks lock
 * the user's row, so concurrent requests from one user can't both slip under a limit; the global
 * daily cap is a bill guard and may overshoot by the few requests in flight at the same moment.
 */
@Service
public class UsageService {
    private static final DateTimeFormatter RESET_DATE = DateTimeFormatter.ofPattern("d MMM", Locale.ENGLISH);

    private final UsageRepository repository;
    private final LimitsProperties limits;
    private final Clock clock;

    @Autowired
    public UsageService(UsageRepository repository, LimitsProperties limits) {
        this(repository, limits, Clock.systemUTC());
    }

    UsageService(UsageRepository repository, LimitsProperties limits, Clock clock) {
        this.repository = repository;
        this.limits = limits;
        this.clock = clock;
    }

    /** Early check at upload time, before the recording is stored. Nothing is charged here. */
    public void checkRecordingAllowed(UUID userId, int seconds) {
        int maxSeconds = limits.maxRecordingMinutes() * 60;
        if (seconds > maxSeconds) {
            throw new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE,
                    "Recordings can be at most " + limits.maxRecordingMinutes() + " minutes long.");
        }
        if (!repository.isUnlimited(userId)) {
            checkMonthlyAudio(userId, seconds);
        }
    }

    /** Charges a session's audio once; charging the same session again (a retry) is free. */
    @Transactional
    public void chargeAudio(UUID userId, UUID sessionId, int seconds) {
        boolean unlimited = repository.lockUserAndCheckUnlimited(userId);
        if (repository.hasAudioCharge(sessionId)) return;
        if (!unlimited) checkMonthlyAudio(userId, seconds);
        long globalUsed = repository.globalSumSince(UsageKind.AUDIO_SECONDS, dayStart());
        if (globalUsed + seconds > limits.globalDailyAudioMinutes() * 60L) {
            throw new LimitExceededException(
                    "MindCue has reached today's processing capacity. Please try again after midnight UTC.",
                    nextDayStart());
        }
        repository.insert(userId, sessionId, UsageKind.AUDIO_SECONDS, seconds);
    }

    /** Counts one Ask Memory call; give it back with {@link #releaseAiRequest} if the call fails. */
    @Transactional
    public UUID reserveAiRequest(UUID userId) {
        boolean unlimited = repository.lockUserAndCheckUnlimited(userId);
        if (!unlimited && repository.sumSince(userId, UsageKind.AI_REQUEST, dayStart()) >= limits.dailyAiRequests()) {
            throw new LimitExceededException("You've reached today's limit of " + limits.dailyAiRequests()
                    + " Ask Memory questions. It resets at midnight UTC.", nextDayStart());
        }
        return repository.insert(userId, null, UsageKind.AI_REQUEST, 1);
    }

    public void releaseAiRequest(UUID eventId) {
        repository.delete(eventId);
    }

    public UsageSummary summary(UUID userId) {
        long audioSeconds = repository.sumSince(userId, UsageKind.AUDIO_SECONDS, monthStart());
        long aiRequests = repository.sumSince(userId, UsageKind.AI_REQUEST, dayStart());
        return new UsageSummary(
                repository.isUnlimited(userId),
                (int) ((audioSeconds + 59) / 60),
                limits.monthlyAudioMinutes(),
                nextMonthStart(),
                (int) aiRequests,
                limits.dailyAiRequests(),
                nextDayStart(),
                limits.maxRecordingMinutes());
    }

    private void checkMonthlyAudio(UUID userId, int seconds) {
        long used = repository.sumSince(userId, UsageKind.AUDIO_SECONDS, monthStart());
        long limit = limits.monthlyAudioMinutes() * 60L;
        if (used + seconds <= limit) return;
        String resets = " Resets on " + RESET_DATE.format(nextMonthStart().atOffset(ZoneOffset.UTC)) + ".";
        long remainingMinutes = Math.max(0, limit - used) / 60;
        String message = used >= limit
                ? "You've used your " + limits.monthlyAudioMinutes() + " audio minutes for this month." + resets
                : "This recording is " + (seconds + 59) / 60 + " min but only " + remainingMinutes
                        + " min are left this month." + resets;
        throw new LimitExceededException(message, nextMonthStart());
    }

    private LocalDate today() {
        return LocalDate.now(clock.withZone(ZoneOffset.UTC));
    }

    private Instant dayStart() {
        return today().atStartOfDay().toInstant(ZoneOffset.UTC);
    }

    private Instant nextDayStart() {
        return today().plusDays(1).atStartOfDay().toInstant(ZoneOffset.UTC);
    }

    private Instant monthStart() {
        return today().withDayOfMonth(1).atStartOfDay().toInstant(ZoneOffset.UTC);
    }

    private Instant nextMonthStart() {
        return today().withDayOfMonth(1).plusMonths(1).atStartOfDay().toInstant(ZoneOffset.UTC);
    }
}
