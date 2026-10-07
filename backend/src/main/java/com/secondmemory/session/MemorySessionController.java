package com.secondmemory.session;

import com.secondmemory.audio.AudioDuration;
import com.secondmemory.audio.AudioStorageService;
import com.secondmemory.auth.CurrentUser;
import com.secondmemory.usage.UsageService;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Map;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/memory/sessions")
public class MemorySessionController {
    private final MemorySessionService sessions;
    private final AudioStorageService audioStorage;
    private final MemorySessionRepository repository;
    private final UsageService usage;

    public MemorySessionController(MemorySessionService sessions,
                                   AudioStorageService audioStorage,
                                   MemorySessionRepository repository,
                                   UsageService usage) {
        this.sessions = sessions;
        this.audioStorage = audioStorage;
        this.repository = repository;
        this.usage = usage;
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
        UUID userId = CurrentUser.id(auth);
        sessions.get(id, userId);
        String name = file.getOriginalFilename() == null ? "" : file.getOriginalFilename().toLowerCase(Locale.ROOT);
        if (!name.endsWith(".m4a") && !name.endsWith(".mp4")) {
            throw new IllegalArgumentException("Recordings must be .m4a or .mp4 files");
        }
        int seconds = measure(file);
        usage.checkRecordingAllowed(userId, seconds);
        String path = audioStorage.store(id, file);
        repository.markAudioReceived(id, path, seconds);
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

    /** Measures the uploaded recording on the server; client-reported durations are not trusted. */
    private static int measure(MultipartFile file) throws IOException {
        Path tmp = Files.createTempFile("upload-measure-", ".m4a");
        try {
            try (var in = file.getInputStream()) {
                Files.copy(in, tmp, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            }
            return AudioDuration.seconds(tmp);
        } finally {
            Files.deleteIfExists(tmp);
        }
    }
}
