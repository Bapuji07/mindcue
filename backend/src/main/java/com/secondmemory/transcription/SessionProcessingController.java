package com.secondmemory.transcription;

import com.secondmemory.auth.CurrentUser;
import com.secondmemory.common.LimitExceededException;
import com.secondmemory.config.LimitsProperties;
import com.secondmemory.session.MemorySession;
import com.secondmemory.session.MemorySessionRepository;
import com.secondmemory.session.MemorySessionService;
import com.secondmemory.usage.UsageService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/memory/sessions/{sessionId}")
public class SessionProcessingController {
    private final MemorySessionService sessions;
    private final MemorySessionRepository sessionRepository;
    private final SessionProcessingOrchestrator orchestrator;
    private final UsageService usage;
    private final LimitsProperties limits;

    public SessionProcessingController(MemorySessionService sessions,
                                       MemorySessionRepository sessionRepository,
                                       SessionProcessingOrchestrator orchestrator,
                                       UsageService usage,
                                       LimitsProperties limits) {
        this.sessions = sessions;
        this.sessionRepository = sessionRepository;
        this.orchestrator = orchestrator;
        this.usage = usage;
        this.limits = limits;
    }

    /**
     * Starts transcription + memory extraction in the background and returns immediately.
     * Poll GET /sessions/{id}/detail for status (session.status becomes COMPLETED or FAILED).
     * The session is PROCESSING before this returns; 409 means a run is already active or the
     * session is already COMPLETED, 429 means a usage limit or the retry limit was reached.
     */
    @PostMapping("/process")
    public ResponseEntity<Map<String, Object>> process(@PathVariable UUID sessionId, Authentication auth) {
        UUID userId = CurrentUser.id(auth);
        MemorySession session = sessions.get(sessionId, userId);
        int maxAttempts = limits.maxProcessingAttempts();
        if (sessionRepository.processingAttempts(sessionId) >= maxAttempts) {
            throw attemptsExhausted();
        }
        // Audio still present means it has not been transcribed yet; charge it once (retries are free).
        boolean hasAudio = session.audioUri() != null && !session.audioUri().isBlank();
        if (hasAudio && session.durationSeconds() != null) {
            usage.chargeAudio(userId, sessionId, session.durationSeconds());
        }
        if (!sessionRepository.claimForProcessing(sessionId, userId, maxAttempts)) {
            if (sessionRepository.processingAttempts(sessionId) >= maxAttempts) throw attemptsExhausted();
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "This conversation is already processing or has already completed");
        }
        orchestrator.runProcessing(sessionId, userId);
        return ResponseEntity.status(HttpStatus.ACCEPTED)
                .body(Map.of("sessionId", sessionId, "status", "PROCESSING"));
    }

    private LimitExceededException attemptsExhausted() {
        return new LimitExceededException(
                "This conversation failed to process too many times. Delete it and record again.", null);
    }
}
