package com.secondmemory.session;

import com.secondmemory.common.NotFoundException;
import com.secondmemory.audio.AudioStorageService;
import com.secondmemory.memory.MemoryRepository;
import com.secondmemory.transcript.TranscriptRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class MemorySessionService {
    private final MemorySessionRepository repository;
    private final MemoryRepository memories;
    private final TranscriptRepository transcripts;
    private final AudioStorageService audioStorage;

    public MemorySessionService(MemorySessionRepository repository,
                                MemoryRepository memories,
                                TranscriptRepository transcripts,
                                AudioStorageService audioStorage) {
        this.repository = repository;
        this.memories = memories;
        this.transcripts = transcripts;
        this.audioStorage = audioStorage;
    }

    public MemorySession create(CreateSessionRequest request) {
        return repository.create(request);
    }

    public MemorySession get(UUID id) {
        return repository.findById(id)
                .orElseThrow(() -> new NotFoundException("Memory session not found: " + id));
    }

    public MemorySession finish(UUID id, Instant endedAt, Integer durationSeconds) {
        get(id);
        repository.finish(id, endedAt, durationSeconds);
        return get(id);
    }

    public List<MemorySession> list(UUID userId, int limit) {
        return repository.listByUser(userId, Math.max(1, Math.min(limit, 200)));
    }

    public MemorySessionDetail detail(UUID id, UUID userId) {
        MemorySession session = getForUser(id, userId);
        return new MemorySessionDetail(session, memories.listBySession(id, userId), transcripts.findBySession(id));
    }

    public MemorySession rename(UUID id, UUID userId, String title) {
        getForUser(id, userId);
        repository.updateTitle(id, userId, title.trim());
        return getForUser(id, userId);
    }

    @Transactional(rollbackFor = Exception.class)
    public void delete(UUID id, UUID userId) throws IOException {
        MemorySession session = getForUser(id, userId);
        memories.deleteBySession(id, userId);
        repository.delete(id, userId);
        audioStorage.delete(session.audioUri());
    }

    private MemorySession getForUser(UUID id, UUID userId) {
        return repository.findByIdAndUser(id, userId)
                .orElseThrow(() -> new NotFoundException("Memory session not found: " + id));
    }
}
