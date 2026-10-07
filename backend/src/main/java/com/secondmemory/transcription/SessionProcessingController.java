package com.secondmemory.transcription;

import com.secondmemory.auth.CurrentUser;
import com.secondmemory.session.MemorySessionRepository;
import com.secondmemory.session.MemorySessionService;
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
    private final TranscriptionService transcriptionService;
    private final MemorySessionService sessions;
    private final MemorySessionRepository sessionRepository;
    private final SessionProcessingOrchestrator orchestrator;

    public SessionProcessingController(TranscriptionService transcriptionService,
                                       MemorySessionService sessions,
                                       MemorySessionRepository sessionRepository,
                                       SessionProcessingOrchestrator orchestrator) {
        this.transcriptionService = transcriptionService;
        this.sessions = sessions;
        this.sessionRepository = sessionRepository;
        this.orchestrator = orchestrator;
    }

    @PostMapping("/transcribe")
    public TranscriptionResponse transcribe(@PathVariable UUID sessionId, Authentication auth) {
        return transcriptionService.transcribe(sessionId, CurrentUser.id(auth));
    }

    /**
     * Starts transcription + memory extraction in the background and returns immediately.
     * Poll GET /sessions/{id}/detail for status (session.status becomes COMPLETED or FAILED).
     * The session is PROCESSING before this returns; 409 means a run is already active or the
     * session is already COMPLETED.
     */
    @PostMapping("/process")
    public ResponseEntity<Map<String, Object>> process(@PathVariable UUID sessionId, Authentication auth) {
        UUID userId = CurrentUser.id(auth);
        sessions.get(sessionId, userId);
        if (!sessionRepository.claimForProcessing(sessionId, userId)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "This conversation is already processing or has already completed");
        }
        orchestrator.runProcessing(sessionId, userId);
        return ResponseEntity.status(HttpStatus.ACCEPTED)
                .body(Map.of("sessionId", sessionId, "status", "PROCESSING"));
    }
}
