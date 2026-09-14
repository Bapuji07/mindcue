package com.secondmemory.transcription;

import com.secondmemory.ai.AiProviderRegistry;
import com.secondmemory.ai.TranscriptionProvider;
import com.secondmemory.ai.dto.TranscriptionResult;
import com.secondmemory.ai.dto.TranscriptionSegment;
import com.secondmemory.config.AiProperties;
import com.secondmemory.session.MemorySession;
import com.secondmemory.session.MemorySessionRepository;
import com.secondmemory.session.MemorySessionService;
import com.secondmemory.session.SessionStatus;
import com.secondmemory.transcript.CreateTranscriptChunkRequest;
import com.secondmemory.transcript.TranscriptChunk;
import com.secondmemory.transcript.TranscriptRepository;
import org.springframework.stereotype.Service;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
public class TranscriptionService {
    private final AiProviderRegistry providers;
    private final AiProperties aiProperties;
    private final MemorySessionService sessions;
    private final MemorySessionRepository sessionRepository;
    private final TranscriptRepository transcripts;

    public TranscriptionService(AiProviderRegistry providers,
                                AiProperties aiProperties,
                                MemorySessionService sessions,
                                MemorySessionRepository sessionRepository,
                                TranscriptRepository transcripts) {
        this.providers = providers;
        this.aiProperties = aiProperties;
        this.sessions = sessions;
        this.sessionRepository = sessionRepository;
        this.transcripts = transcripts;
    }

    public TranscriptionResponse transcribe(UUID sessionId) {
        MemorySession session = sessions.get(sessionId);
        if (session.audioUri() == null || session.audioUri().isBlank()) {
            throw new IllegalArgumentException("No audio has been uploaded for session " + sessionId);
        }

        Path audioPath = Path.of(session.audioUri()).toAbsolutePath().normalize();
        if (!Files.isRegularFile(audioPath)) {
            throw new IllegalArgumentException("Uploaded audio file cannot be found: " + audioPath);
        }

        AiProperties.Transcription config = aiProperties.transcription();
        TranscriptionProvider provider = providers.transcription(config.provider());
        sessionRepository.updateStatus(sessionId, SessionStatus.TRANSCRIBING);

        try {
            TranscriptionResult result = provider.transcribe(audioPath);
            transcripts.deleteBySession(sessionId);

            List<TranscriptionSegment> segments = result.segments();
            if (segments == null || segments.isEmpty()) {
                segments = List.of(new TranscriptionSegment(null, null, null, result.text(), null));
            }

            int sequence = 0;
            for (TranscriptionSegment segment : segments) {
                if (segment.text() == null || segment.text().isBlank()) {
                    continue;
                }
                transcripts.create(sessionId, new CreateTranscriptChunkRequest(
                        sequence++,
                        segment.startMs(),
                        segment.endMs(),
                        segment.speakerLabel(),
                        segment.text(),
                        segment.confidence()
                ));
            }

            List<TranscriptChunk> savedChunks = transcripts.findBySession(sessionId);
            if (savedChunks.isEmpty()) {
                throw new IllegalStateException("Transcription completed but no transcript chunks were created");
            }

            sessionRepository.updateStatus(sessionId, SessionStatus.TRANSCRIPTION_COMPLETE);
            return new TranscriptionResponse(
                    sessionId,
                    provider.name(),
                    config.model(),
                    result.text(),
                    new ArrayList<>(savedChunks),
                    SessionStatus.TRANSCRIPTION_COMPLETE
            );
        } catch (RuntimeException ex) {
            sessionRepository.updateStatus(sessionId, SessionStatus.FAILED);
            throw ex;
        }
    }
}
