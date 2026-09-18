package com.secondmemory.session;

import com.secondmemory.audio.AudioStorageService;
import com.secondmemory.auth.CurrentUser;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.Instant;
import java.util.Map;
import java.util.List;
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
    public MemorySession create(@Valid @RequestBody CreateSessionRequest request, Authentication auth) {
        return sessions.create(CurrentUser.id(auth), request);
    }

    @GetMapping("/{id}")
    public MemorySession get(@PathVariable UUID id, Authentication auth) {
        return sessions.get(id, CurrentUser.id(auth));
    }

    @GetMapping
    public Map<String, List<MemorySession>> list(@RequestParam(defaultValue = "50") int limit, Authentication auth) {
        return Map.of("sessions", sessions.list(CurrentUser.id(auth), limit));
    }

    @GetMapping("/{id}/detail")
    public MemorySessionDetail detail(@PathVariable UUID id, Authentication auth) {
        return sessions.detail(id, CurrentUser.id(auth));
    }

    @PatchMapping("/{id}")
    public MemorySession rename(@PathVariable UUID id,
                                @Valid @RequestBody RenameSessionRequest request,
                                Authentication auth) {
        return sessions.rename(id, CurrentUser.id(auth), request.title());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id, Authentication auth) throws IOException {
        sessions.delete(id, CurrentUser.id(auth));
        return ResponseEntity.noContent().build();
    }

    @PostMapping(value = "/{id}/audio", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Map<String, Object> uploadAudio(@PathVariable UUID id,
                                           @RequestPart("file") MultipartFile file,
                                           Authentication auth) throws IOException {
        sessions.get(id, CurrentUser.id(auth));
        String path = audioStorage.store(id, file);
        repository.markAudioReceived(id, path);
        return Map.of("sessionId", id, "audioUri", path, "status", SessionStatus.AUDIO_RECEIVED);
    }

    @PostMapping("/{id}/finish")
    public MemorySession finish(@PathVariable UUID id,
                                @RequestBody(required = false) FinishSessionRequest request,
                                Authentication auth) {
        Instant endedAt = request == null || request.endedAt() == null
                ? Instant.now()
                : request.endedAt().toInstant();
        Integer duration = request == null ? null : request.durationSeconds();
        return sessions.finish(id, CurrentUser.id(auth), endedAt, duration);
    }
}
