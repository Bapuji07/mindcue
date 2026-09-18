package com.secondmemory.transcription;

import com.secondmemory.memory.MemoryExtractionService;
import com.secondmemory.session.MemorySessionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class SessionProcessingOrchestrator {
    private static final Logger log = LoggerFactory.getLogger(SessionProcessingOrchestrator.class);

    private final TranscriptionService transcriptionService;
    private final MemoryExtractionService extractionService;
    private final MemorySessionRepository sessionRepository;

    public SessionProcessingOrchestrator(TranscriptionService transcriptionService,
                                         MemoryExtractionService extractionService,
                                         MemorySessionRepository sessionRepository) {
        this.transcriptionService = transcriptionService;
        this.extractionService = extractionService;
        this.sessionRepository = sessionRepository;
    }

    @Async
    public void runProcessing(UUID sessionId, UUID userId) {
        try {
            transcriptionService.transcribe(sessionId, userId);
            extractionService.extract(sessionId, userId);
        } catch (Exception ex) {
            log.warn("Session processing failed for session {}: {}", sessionId, ex.getMessage());
            String message = ex.getMessage();
            sessionRepository.markFailed(sessionId, message == null || message.isBlank() ? "Processing failed" : message);
        }
    }
}
