package com.secondmemory.transcription;

import com.secondmemory.memory.MemoryExtractionService;
import com.secondmemory.memory.MemoryRepository;
import com.secondmemory.session.MemorySession;
import com.secondmemory.session.MemorySessionRepository;
import com.secondmemory.session.MemorySessionService;
import com.secondmemory.session.SessionStatus;
import com.secondmemory.transcript.TranscriptChunk;
import com.secondmemory.transcript.TranscriptRepository;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SessionProcessingOrchestratorTest {
    private final UUID sessionId = UUID.randomUUID();
    private final UUID userId = UUID.randomUUID();
    private final TranscriptionService transcription = mock(TranscriptionService.class);
    private final MemoryExtractionService extraction = mock(MemoryExtractionService.class);
    private final MemorySessionRepository sessionRepository = mock(MemorySessionRepository.class);
    private final MemorySessionService sessions = mock(MemorySessionService.class);
    private final TranscriptRepository transcripts = mock(TranscriptRepository.class);
    private final MemoryRepository memories = mock(MemoryRepository.class);
    private final SessionProcessingOrchestrator orchestrator = new SessionProcessingOrchestrator(
            transcription, extraction, sessionRepository, sessions, transcripts, memories);

    private MemorySession session(SessionStatus status, String audioUri) {
        Instant now = Instant.now();
        return new MemorySession(sessionId, userId, "t", status, "ANDROID", now, null, "UTC",
                audioUri, null, null, null, now, now, null);
    }

    @Test
    void freshSessionTranscribesThenExtracts() {
        when(sessions.get(sessionId, userId)).thenReturn(session(SessionStatus.AUDIO_RECEIVED, "s3://audio"));
        when(transcripts.findBySession(sessionId)).thenReturn(List.of());
        orchestrator.runProcessing(sessionId, userId);
        verify(transcription).transcribe(sessionId, userId);
        verify(memories).deleteBySession(sessionId, userId);
        verify(extraction).extract(sessionId, userId);
    }

    @Test
    void stuckProcessingSessionClearsPartialMemoriesBeforeExtracting() {
        // Server restarted mid-extraction: the session stayed PROCESSING with some memories saved.
        when(sessions.get(sessionId, userId)).thenReturn(session(SessionStatus.PROCESSING, null));
        when(transcripts.findBySession(sessionId)).thenReturn(List.of(mock(TranscriptChunk.class)));
        orchestrator.runProcessing(sessionId, userId);
        var order = inOrder(memories, extraction);
        order.verify(memories).deleteBySession(sessionId, userId);
        order.verify(extraction).extract(sessionId, userId);
        verify(transcription, never()).transcribe(sessionId, userId);
    }

    @Test
    void failedAfterTranscriptionResumesAtExtractionAndClearsLeftovers() {
        when(sessions.get(sessionId, userId)).thenReturn(session(SessionStatus.FAILED, null));
        when(transcripts.findBySession(sessionId)).thenReturn(List.of(mock(TranscriptChunk.class)));
        orchestrator.runProcessing(sessionId, userId);
        verify(memories).deleteBySession(sessionId, userId);
        verify(transcription, never()).transcribe(sessionId, userId);
        verify(extraction).extract(sessionId, userId);
    }

    @Test
    void failureIsRecordedOnTheSession() {
        when(sessions.get(sessionId, userId)).thenReturn(session(SessionStatus.AUDIO_RECEIVED, "s3://audio"));
        when(transcripts.findBySession(sessionId)).thenReturn(List.of());
        doThrow(new IllegalStateException("boom")).when(transcription).transcribe(sessionId, userId);
        orchestrator.runProcessing(sessionId, userId);
        verify(sessionRepository).markFailed(sessionId, "boom");
        verify(extraction, never()).extract(sessionId, userId);
    }
}
