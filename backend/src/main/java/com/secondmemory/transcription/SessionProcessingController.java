package com.secondmemory.transcription;

import com.secondmemory.auth.CurrentUser;
import com.secondmemory.session.MemorySessionService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/memory/sessions/{sessionId}")
public class SessionProcessingController {
    private final TranscriptionService transcriptionService;
    private final MemorySessionService sessions;
    private final SessionProcessingOrchestrator orchestrator;

    public SessionProcessingController(TranscriptionService transcriptionService,
                                       MemorySessionService sessions,
                                       SessionProcessingOrchestrator orchestrator) {
        this.transcriptionService = transcriptionService;
        this.sessions = sessions;
        this.orchestrator = orchestrator;
    }

    @PostMapping("/transcribe")
    public TranscriptionResponse transcribe(@PathVariable UUID sessionId, Authentication auth) {
        return transcriptionService.transcribe(sessionId, CurrentUser.id(auth));
    }

    /**
     * Starts transcription + memory extraction in the background and returns immediately.
     * Poll GET /sessions/{id}/detail for status (session.status becomes COMPLETED or FAILED).
     */
    @PostMapping("/process")
    public ResponseEntity<Map<String, Object>> process(@PathVariable UUID sessionId, Authentication auth) {
        UUID userId = CurrentUser.id(auth);
        sessions.get(sessionId, userId);
        orchestrator.runProcessing(sessionId, userId);
        return ResponseEntity.status(HttpStatus.ACCEPTED)
                .body(Map.of("sessionId", sessionId, "status", "PROCESSING"));
    }
}
