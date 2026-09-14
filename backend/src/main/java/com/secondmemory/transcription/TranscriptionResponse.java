package com.secondmemory.transcription;

import com.secondmemory.session.SessionStatus;
import com.secondmemory.transcript.TranscriptChunk;

import java.util.List;
import java.util.UUID;

public record TranscriptionResponse(
        UUID sessionId,
        String provider,
        String model,
        String transcriptText,
        List<TranscriptChunk> chunks,
        SessionStatus status
) {}
