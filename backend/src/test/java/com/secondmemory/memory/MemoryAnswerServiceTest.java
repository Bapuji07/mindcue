package com.secondmemory.memory;

import com.secondmemory.ai.AiProviderRegistry;
import com.secondmemory.ai.ChatAiProvider;
import com.secondmemory.config.AiProperties;
import com.secondmemory.usage.UsageService;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MemoryAnswerServiceTest {
    private final UUID userId = UUID.randomUUID();
    private final UUID reservation = UUID.randomUUID();
    private final AiProviderRegistry providers = mock(AiProviderRegistry.class);
    private final AiProperties aiProperties = mock(AiProperties.class);
    private final MemoryRepository memories = mock(MemoryRepository.class);
    private final UsageService usage = mock(UsageService.class);
    private final ChatAiProvider chat = mock(ChatAiProvider.class);
    private final MemoryAnswerService service = new MemoryAnswerService(providers, aiProperties, memories, usage);

    private void withOneMatchingMemory() {
        Instant now = Instant.now();
        MemoryRecord memory = new MemoryRecord(UUID.randomUUID(), userId, UUID.randomUUID(), MemoryType.TASK,
                "Pay invoice", "Pay the invoice on Friday", new BigDecimal("0.5"), null, ResolutionStatus.OPEN,
                null, null, "gemini", "m", "v1", true, now, now, null, false);
        when(memories.list(userId, null, 500)).thenReturn(List.of(memory));
        when(aiProperties.chat()).thenReturn(mock(AiProperties.Chat.class));
        when(providers.chat(any())).thenReturn(chat);
        when(usage.reserveAiRequest(userId)).thenReturn(reservation);
    }

    @Test
    void answeredQuestionUsesOneRequest() {
        withOneMatchingMemory();
        when(chat.generateText(anyString(), anyString())).thenReturn("Friday.");
        AskMemoryResponse response = service.ask(new AskMemoryRequest("When is the invoice due?", null), userId);
        assertEquals("Friday.", response.answer());
        verify(usage).reserveAiRequest(userId);
        verify(usage, never()).releaseAiRequest(any());
    }

    @Test
    void failedModelCallGivesTheRequestBack() {
        withOneMatchingMemory();
        when(chat.generateText(anyString(), anyString())).thenThrow(new IllegalStateException("provider down"));
        assertThrows(IllegalStateException.class,
                () -> service.ask(new AskMemoryRequest("When is the invoice due?", null), userId));
        verify(usage).releaseAiRequest(reservation);
    }

    @Test
    void questionWithNoMemoriesDoesNotCount() {
        when(memories.list(userId, null, 500)).thenReturn(List.of());
        service.ask(new AskMemoryRequest("anything?", null), userId);
        verify(usage, never()).reserveAiRequest(any());
    }
}
