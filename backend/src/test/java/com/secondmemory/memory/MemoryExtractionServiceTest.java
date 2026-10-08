package com.secondmemory.memory;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
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
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MemoryExtractionServiceTest {
    private final UUID sessionId = UUID.randomUUID();
    private final UUID userId = UUID.randomUUID();
    private final UUID chunk1 = UUID.randomUUID();
    private final UUID chunk2 = UUID.randomUUID();
    private final AiProviderRegistry providers = mock(AiProviderRegistry.class);
    private final AiProperties aiProperties = mock(AiProperties.class);
    private final TranscriptRepository transcripts = mock(TranscriptRepository.class);
    private final MemoryRepository memories = mock(MemoryRepository.class);
    private final MemorySessionService sessions = mock(MemorySessionService.class);
    private final MemorySessionRepository sessionRepository = mock(MemorySessionRepository.class);
    private final ChatAiProvider chat = mock(ChatAiProvider.class);
    private final MemoryExtractionService service = new MemoryExtractionService(
            providers, aiProperties, transcripts, memories, sessions, sessionRepository);

    private void setUp(String selfSpeaker, String speaker1, String speaker2, ExtractedMemory... extracted) {
        Instant now = Instant.now();
        when(sessions.get(sessionId, userId)).thenReturn(new MemorySession(sessionId, userId, "t", SessionStatus.PROCESSING,
                "ANDROID", now, null, "UTC", null, 60, null, null, now, now, selfSpeaker));
        when(transcripts.findBySession(sessionId)).thenReturn(List.of(
                new TranscriptChunk(chunk1, sessionId, 0, null, null, speaker1, "Can you send the invoice?", null, now),
                new TranscriptChunk(chunk2, sessionId, 1, null, null, speaker2, "I'll send it by Friday.", null, now)));
        when(aiProperties.chat()).thenReturn(new AiProperties.Chat("p", "u", "k", "m", 0.2));
        when(providers.chat(any())).thenReturn(chat);
        when(chat.generateStructured(anyString(), anyString(), eq(ExtractedMemoryResponse.class)))
                .thenReturn(new ExtractedMemoryResponse("summary", List.of(extracted)));
        when(memories.insert(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), anyBoolean()))
                .thenReturn(UUID.randomUUID());
    }

    private static ExtractedMemory memory(String type, String owner, String... sources) {
        return new ExtractedMemory(type, "Send invoice", "Send the invoice by Friday", new BigDecimal("0.8"),
                new BigDecimal("0.9"), null, null, null, List.of(sources), owner);
    }

    @Test
    void chunkNumbersMapToChunksAndOwnersAreSaved() {
        setUp("Speaker 2", "Speaker 1", "Speaker 2", memory("PROMISE", "Speaker 2", "1"));
        service.extract(sessionId, userId);
        verify(memories).insert(eq(userId), eq(sessionId), eq(MemoryType.PROMISE), any(), any(), any(), any(),
                eq(ResolutionStatus.OPEN), any(), any(), any(), any(), eq("memory-extraction-v2"), eq("Speaker 2"), eq(true));
        verify(memories).linkSource(any(), eq(chunk2));
        verify(sessionRepository).updateSummary(sessionId, "summary", SessionStatus.COMPLETED);
    }

    @Test
    void otherSpeakersOwnersAreNotTheUser() {
        setUp("Speaker 1", "Speaker 1", "Speaker 2", memory("PROMISE", "Speaker 2", "1"));
        service.extract(sessionId, userId);
        verify(memories).insert(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(),
                eq("Speaker 2"), eq(false));
    }

    @Test
    void memoriesWithoutValidSourcesAreSkippedNotFatal() {
        setUp(null, "Speaker 1", "Speaker 2",
                memory("TASK", null, "99", "not-a-chunk"),
                memory("TASK", null, "0", chunk2.toString()));
        service.extract(sessionId, userId);
        verify(memories, times(1)).insert(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(),
                any(), isNull(), eq(false));
        verify(memories).linkSource(any(), eq(chunk1));
        verify(memories).linkSource(any(), eq(chunk2));
        verify(sessionRepository).updateSummary(sessionId, "summary", SessionStatus.COMPLETED);
    }

    @Test
    void unknownTypeBecomesANote() {
        setUp(null, null, null, memory("REMINDER", null, "0"));
        service.extract(sessionId, userId);
        verify(memories).insert(any(), any(), eq(MemoryType.NOTE), any(), any(), any(), any(), eq(ResolutionStatus.NONE),
                any(), any(), any(), any(), any(), any(), anyBoolean());
    }

    @Test
    void aSingleSpeakerIsTakenToBeTheUser() {
        setUp(null, "Speaker 1", "Speaker 1", memory("TASK", "Speaker 1", "0"));
        service.extract(sessionId, userId);
        verify(sessionRepository).updateSelfSpeaker(sessionId, "Speaker 1");
        verify(memories).insert(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(),
                eq("Speaker 1"), eq(true));
    }

    @Test
    void severalSpeakersLeaveTheChoiceToTheUser() {
        setUp(null, "Speaker 1", "Speaker 2", memory("TASK", "Speaker 1", "0"));
        service.extract(sessionId, userId);
        verify(sessionRepository, never()).updateSelfSpeaker(any(), any());
    }

    @Test
    void chunkNumbersFromTheModelAreReadAsText() throws Exception {
        ObjectMapper mapper = new ObjectMapper().registerModule(new JavaTimeModule());
        ExtractedMemoryResponse response = mapper.readValue("""
                {"summary":"s","memories":[{"type":"TASK","content":"c","sourceChunkIds":[3, 4],"owner":"Speaker 2"}]}
                """, ExtractedMemoryResponse.class);
        assertThat(response.memories().get(0).sourceChunkIds()).containsExactly("3", "4");
        assertThat(response.memories().get(0).owner()).isEqualTo("Speaker 2");
    }
}
