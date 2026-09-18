package com.secondmemory.memory;

import com.secondmemory.ai.AiProviderRegistry;
import com.secondmemory.ai.ChatAiProvider;
import com.secondmemory.ai.dto.ExtractedMemory;
import com.secondmemory.ai.dto.ExtractedMemoryResponse;
import com.secondmemory.config.AiProperties;
import com.secondmemory.session.MemorySession;
import com.secondmemory.session.MemorySessionRepository;
import com.secondmemory.session.MemorySessionService;
import com.secondmemory.session.SessionStatus;
import com.secondmemory.transcript.TranscriptChunk;
import com.secondmemory.transcript.TranscriptRepository;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class MemoryExtractionService {
    private static final String PROMPT_VERSION = "memory-extraction-v1";

    private final AiProviderRegistry providers;
    private final AiProperties aiProperties;
    private final TranscriptRepository transcripts;
    private final MemoryRepository memories;
    private final MemorySessionService sessions;
    private final MemorySessionRepository sessionRepository;

    public MemoryExtractionService(AiProviderRegistry providers,
                                   AiProperties aiProperties,
                                   TranscriptRepository transcripts,
                                   MemoryRepository memories,
                                   MemorySessionService sessions,
                                   MemorySessionRepository sessionRepository) {
        this.providers = providers;
        this.aiProperties = aiProperties;
        this.transcripts = transcripts;
        this.memories = memories;
        this.sessions = sessions;
        this.sessionRepository = sessionRepository;
    }

    public ExtractedMemoryResponse extract(UUID sessionId, UUID userId) {
        MemorySession session = sessions.get(sessionId, userId);
        List<TranscriptChunk> chunks = transcripts.findBySession(sessionId);
        if (chunks.isEmpty()) {
            throw new IllegalArgumentException("No transcript chunks exist for session " + sessionId);
        }

        ChatAiProvider provider = providers.chat(aiProperties.chat().provider());
        String systemPrompt = readPrompt();
        String userPrompt = buildUserPrompt(session, chunks);
        sessionRepository.updateStatus(sessionId, SessionStatus.PROCESSING);

        ExtractedMemoryResponse response = provider.generateStructured(
                systemPrompt,
                userPrompt,
                ExtractedMemoryResponse.class
        );

        java.util.Set<UUID> validChunkIds = chunks.stream()
                .map(TranscriptChunk::id)
                .collect(java.util.stream.Collectors.toSet());

        if (response.memories() != null) {
            for (ExtractedMemory extracted : response.memories()) {
                persist(session, extracted, validChunkIds);
            }
        }

        sessionRepository.updateSummary(sessionId, response.summary(), SessionStatus.COMPLETED);
        return response;
    }

    private void persist(MemorySession session, ExtractedMemory extracted, java.util.Set<UUID> validChunkIds) {
        if (extracted.type() == null || extracted.content() == null || extracted.content().isBlank()) {
            throw new IllegalStateException("AI returned a memory without required type/content");
        }
        if (extracted.sourceChunkIds() == null || extracted.sourceChunkIds().isEmpty()) {
            throw new IllegalStateException("AI returned a memory without transcript provenance");
        }

        java.util.List<UUID> sourceIds = extracted.sourceChunkIds().stream().map(value -> {
            try {
                return UUID.fromString(value);
            } catch (IllegalArgumentException ex) {
                throw new IllegalStateException("AI returned an invalid transcript chunk id: " + value);
            }
        }).toList();
        if (!validChunkIds.containsAll(sourceIds)) {
            throw new IllegalStateException("AI returned transcript provenance outside the current session");
        }

        MemoryType type = MemoryType.valueOf(extracted.type().toUpperCase());
        ResolutionStatus status = extracted.resolutionStatus() == null || extracted.resolutionStatus().isBlank()
                ? defaultStatus(type)
                : ResolutionStatus.valueOf(extracted.resolutionStatus().toUpperCase());

        UUID memoryId = memories.insert(
                session.userId(),
                session.id(),
                type,
                extracted.title() == null || extracted.title().isBlank() ? extracted.content().substring(0, Math.min(120, extracted.content().length())) : extracted.title(),
                extracted.content(),
                extracted.importance() == null ? new java.math.BigDecimal("0.50") : extracted.importance(),
                extracted.confidence() == null ? new java.math.BigDecimal("0.70") : extracted.confidence(),
                status,
                toInstant(extracted.occurredAt()),
                toInstant(extracted.dueAt()),
                aiProperties.chat().provider(),
                aiProperties.chat().model(),
                PROMPT_VERSION
        );

        for (UUID sourceId : sourceIds) {
            memories.linkSource(memoryId, sourceId);
        }
    }

    private ResolutionStatus defaultStatus(MemoryType type) {
        return switch (type) {
            case TASK, PROMISE, PROBLEM, QUESTION -> ResolutionStatus.OPEN;
            default -> ResolutionStatus.NONE;
        };
    }

    private Instant toInstant(java.time.OffsetDateTime dateTime) {
        return dateTime == null ? null : dateTime.toInstant();
    }

    private String buildUserPrompt(MemorySession session, List<TranscriptChunk> chunks) {
        StringBuilder transcript = new StringBuilder();
        for (TranscriptChunk chunk : chunks) {
            transcript.append("CHUNK_ID: ").append(chunk.id()).append('\n');
            transcript.append("SEQUENCE: ").append(chunk.sequenceNo()).append('\n');
            if (chunk.speakerLabel() != null) {
                transcript.append("SPEAKER: ").append(chunk.speakerLabel()).append('\n');
            }
            transcript.append("TEXT: ").append(chunk.text()).append("\n\n");
        }

        return """
                Conversation start: %s
                Timezone: %s

                Required JSON shape:
                {
                  "summary": "short session summary",
                  "memories": [
                    {
                      "type": "FACT|DECISION|TASK|PROMISE|PROBLEM|IDEA|QUESTION|PREFERENCE|EVENT|NOTE",
                      "title": "short title",
                      "content": "memory content",
                      "importance": 0.0,
                      "confidence": 0.0,
                      "resolutionStatus": "NONE|OPEN|DONE|CANCELLED",
                      "occurredAt": null,
                      "dueAt": null,
                      "sourceChunkIds": ["UUID"]
                    }
                  ]
                }

                Transcript:
                %s
                """.formatted(session.startedAt(), session.timezone(), transcript);
    }

    private String readPrompt() {
        try {
            ClassPathResource resource = new ClassPathResource("prompts/memory-extraction-v1.txt");
            return resource.getContentAsString(StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("Could not load memory extraction prompt", e);
        }
    }
}
