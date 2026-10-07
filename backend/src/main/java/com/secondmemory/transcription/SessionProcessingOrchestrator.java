package com.secondmemory.transcription;

import com.secondmemory.memory.MemoryExtractionService;
import com.secondmemory.memory.MemoryRepository;
import com.secondmemory.session.MemorySession;
import com.secondmemory.session.MemorySessionRepository;
import com.secondmemory.session.MemorySessionService;
import com.secondmemory.transcript.TranscriptRepository;
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
    private final MemorySessionService sessions;
    private final TranscriptRepository transcripts;
    private final MemoryRepository memories;

    public SessionProcessingOrchestrator(TranscriptionService transcriptionService,
                                         MemoryExtractionService extractionService,
                                         MemorySessionRepository sessionRepository,
                                         MemorySessionService sessions,
                                         TranscriptRepository transcripts,
                                         MemoryRepository memories) {
        this.transcriptionService = transcriptionService;
        this.extractionService = extractionService;
        this.sessionRepository = sessionRepository;
        this.sessions = sessions;
        this.transcripts = transcripts;
        this.memories = memories;
    }

    /**
     * Safe to call again after a failed or interrupted run. The recording is deleted once
     * transcription succeeds, so a session that already has a transcript but no audio resumes at
     * extraction instead of failing on the missing audio. The caller has claimed the session (see
     * MemorySessionRepository#claimForProcessing), which never admits a COMPLETED one, so any
     * memories present here are leftovers of an unfinished run and are replaced, not duplicated.
     */
    @Async
    public void runProcessing(UUID sessionId, UUID userId) {
        try {
            MemorySession session = sessions.get(sessionId, userId);
            boolean hasAudio = session.audioUri() != null && !session.audioUri().isBlank();
            boolean hasTranscript = !transcripts.findBySession(sessionId).isEmpty();
            if (hasAudio || !hasTranscript) {
                transcriptionService.transcribe(sessionId, userId);
            }
            memories.deleteBySession(sessionId, userId);
            extractionService.extract(sessionId, userId);
        } catch (Exception ex) {
            log.warn("Session processing failed for session {}: {}", sessionId, ex.getMessage());
            String message = ex.getMessage();
            sessionRepository.markFailed(sessionId, message == null || message.isBlank() ? "Processing failed" : message);
        }
    }
}
