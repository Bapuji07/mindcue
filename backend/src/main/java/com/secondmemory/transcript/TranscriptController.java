package com.secondmemory.transcript;

import com.secondmemory.auth.CurrentUser;
import com.secondmemory.session.MemorySessionService;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/memory/sessions/{sessionId}/transcript-chunks")
public class TranscriptController {
    private final TranscriptRepository repository;
    private final MemorySessionService sessions;

    public TranscriptController(TranscriptRepository repository, MemorySessionService sessions) {
        this.repository = repository;
        this.sessions = sessions;
    }

    @GetMapping
    public List<TranscriptChunk> list(@PathVariable UUID sessionId, Authentication auth) {
        sessions.get(sessionId, CurrentUser.id(auth));
        return repository.findBySession(sessionId);
    }
}
