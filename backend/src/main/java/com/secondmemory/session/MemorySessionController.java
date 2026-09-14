package com.secondmemory.session;

import com.secondmemory.audio.AudioStorageService;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/memory/sessions")
public class MemorySessionController {
    private final MemorySessionService sessions;
    private final AudioStorageService audioStorage;
    private final MemorySessionRepository repository;

    public MemorySessionController(MemorySessionService sessions,
                                   AudioStorageService audioStorage,
                                   MemorySessionRepository repository) {
        this.sessions = sessions;
        this.audioStorage = audioStorage;
        this.repository = repository;
    }

    @PostMapping
    public MemorySession create(@Valid @RequestBody CreateSessionRequest request) {
        return sessions.create(request);
    }

    @GetMapping("/{id}")
    public MemorySession get(@PathVariable UUID id) {
        return sessions.get(id);
    }

    @PostMapping(value = "/{id}/audio", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Map<String, Object> uploadAudio(@PathVariable UUID id,
                                           @RequestPart("file") MultipartFile file) throws IOException {
        sessions.get(id);
        String path = audioStorage.store(id, file);
        repository.markAudioReceived(id, path);
        return Map.of("sessionId", id, "audioUri", path, "status", SessionStatus.AUDIO_RECEIVED);
    }

    @PostMapping("/{id}/finish")
    public MemorySession finish(@PathVariable UUID id, @RequestBody(required = false) FinishSessionRequest request) {
        Instant endedAt = request == null || request.endedAt() == null
                ? Instant.now()
                : request.endedAt().toInstant();
        Integer duration = request == null ? null : request.durationSeconds();
        return sessions.finish(id, endedAt, duration);
    }
}
