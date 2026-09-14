package com.secondmemory.session;

import com.secondmemory.common.NotFoundException;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.UUID;

@Service
public class MemorySessionService {
    private final MemorySessionRepository repository;

    public MemorySessionService(MemorySessionRepository repository) {
        this.repository = repository;
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
}
