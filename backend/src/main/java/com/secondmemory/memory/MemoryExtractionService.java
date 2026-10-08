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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

@Service
public class MemoryExtractionService {
    private static final Logger log = LoggerFactory.getLogger(MemoryExtractionService.class);
    private static final String PROMPT_VERSION = "memory-extraction-v2";
    private static final int MAX_OWNER_LENGTH = 120;

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

        // With a single speaker (a voice memo, say) that speaker is almost always the person recording.
        String selfSpeaker = session.selfSpeaker();
        List<String> speakers = chunks.stream().map(TranscriptChunk::speakerLabel).filter(Objects::nonNull).distinct().toList();
        if (selfSpeaker == null && speakers.size() == 1) {
            selfSpeaker = speakers.get(0);
            sessionRepository.updateSelfSpeaker(sessionId, selfSpeaker);
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

        Map<String, UUID> chunkRefs = chunkReferences(chunks);
        int skipped = 0;
        if (response.memories() != null) {
            for (ExtractedMemory extracted : response.memories()) {
                if (!persist(session, extracted, chunkRefs, selfSpeaker)) skipped++;
            }
        }
        if (skipped > 0) {
            log.warn("Skipped {} extracted memories without usable content or sources in session {}", skipped, sessionId);
        }

        sessionRepository.updateSummary(sessionId, response.summary(), SessionStatus.COMPLETED);
        return response;
    }

    /** The model cites chunks by their number; their UUIDs are accepted too. */
    static Map<String, UUID> chunkReferences(List<TranscriptChunk> chunks) {
        Map<String, UUID> refs = new HashMap<>();
        for (TranscriptChunk chunk : chunks) {
            refs.put(Integer.toString(chunk.sequenceNo()), chunk.id());
            refs.put(chunk.id().toString(), chunk.id());
        }
        return refs;
    }

    /**
     * Saves one extracted memory. A memory the model got wrong (no content, or no source that exists
     * in this conversation) is skipped rather than failing the whole conversation. Returns whether
     * it was saved.
     */
    private boolean persist(MemorySession session, ExtractedMemory extracted, Map<String, UUID> chunkRefs, String selfSpeaker) {
        if (extracted.content() == null || extracted.content().isBlank()) return false;
        List<UUID> sourceIds = extracted.sourceChunkIds() == null ? List.of() : extracted.sourceChunkIds().stream()
                .filter(Objects::nonNull)
                .map(ref -> chunkRefs.get(ref.trim()))
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        if (sourceIds.isEmpty()) return false;

        MemoryType type = parseType(extracted.type());
        ResolutionStatus status = parseStatus(extracted.resolutionStatus(), type);
        String owner = blankToNull(extracted.owner());
        if (owner != null && owner.length() > MAX_OWNER_LENGTH) owner = owner.substring(0, MAX_OWNER_LENGTH);
        boolean ownerIsSelf = owner != null && owner.equals(selfSpeaker);

        UUID memoryId = memories.insert(
                session.userId(),
                session.id(),
                type,
                extracted.title() == null || extracted.title().isBlank() ? extracted.content().substring(0, Math.min(120, extracted.content().length())) : extracted.title(),
                extracted.content(),
                extracted.importance() == null ? new BigDecimal("0.50") : extracted.importance(),
                extracted.confidence() == null ? new BigDecimal("0.70") : extracted.confidence(),
                status,
                toInstant(extracted.occurredAt()),
                toInstant(extracted.dueAt()),
                aiProperties.chat().provider(),
                aiProperties.chat().model(),
                PROMPT_VERSION,
                owner,
                ownerIsSelf
        );

        for (UUID sourceId : sourceIds) {
            memories.linkSource(memoryId, sourceId);
        }
        return true;
    }

    /** Unknown types become notes instead of failing the conversation. */
    private static MemoryType parseType(String value) {
        if (value == null) return MemoryType.NOTE;
        try {
            return MemoryType.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            return MemoryType.NOTE;
        }
    }

    private static ResolutionStatus parseStatus(String value, MemoryType type) {
        if (value == null || value.isBlank()) return defaultStatus(type);
        try {
            return ResolutionStatus.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            return defaultStatus(type);
        }
    }

    private static ResolutionStatus defaultStatus(MemoryType type) {
        return switch (type) {
            case TASK, PROMISE, PROBLEM, QUESTION -> ResolutionStatus.OPEN;
            default -> ResolutionStatus.NONE;
        };
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private Instant toInstant(OffsetDateTime dateTime) {
        return dateTime == null ? null : dateTime.toInstant();
    }

    private String buildUserPrompt(MemorySession session, List<TranscriptChunk> chunks) {
        StringBuilder transcript = new StringBuilder();
        for (TranscriptChunk chunk : chunks) {
            transcript.append("CHUNK ").append(chunk.sequenceNo());
            if (chunk.speakerLabel() != null) {
                transcript.append(" | ").append(chunk.speakerLabel());
            }
            transcript.append('\n').append(chunk.text()).append("\n\n");
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
                      "owner": null,
                      "sourceChunkIds": [1]
                    }
                  ]
                }

                Transcript:
                %s
                """.formatted(session.startedAt(), session.timezone(), transcript);
    }

    private String readPrompt() {
        try {
            ClassPathResource resource = new ClassPathResource("prompts/memory-extraction-v2.txt");
            return resource.getContentAsString(StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("Could not load memory extraction prompt", e);
        }
    }
}
