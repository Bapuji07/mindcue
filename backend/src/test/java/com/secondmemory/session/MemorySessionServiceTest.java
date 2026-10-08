package com.secondmemory.session;

import com.secondmemory.audio.AudioStorageService;
import com.secondmemory.memory.MemoryRepository;
import com.secondmemory.transcript.TranscriptChunk;
import com.secondmemory.transcript.TranscriptRepository;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MemorySessionServiceTest {
    private final UUID sessionId = UUID.randomUUID();
    private final UUID userId = UUID.randomUUID();
    private final MemorySessionRepository repository = mock(MemorySessionRepository.class);
    private final MemoryRepository memories = mock(MemoryRepository.class);
    private final TranscriptRepository transcripts = mock(TranscriptRepository.class);
    private final MemorySessionService service =
            new MemorySessionService(repository, memories, transcripts, mock(AudioStorageService.class));

    private void conversationWithSpeakers(String... speakers) {
        Instant now = Instant.now();
        when(repository.findByIdAndUser(sessionId, userId)).thenReturn(Optional.of(new MemorySession(sessionId, userId,
                "t", SessionStatus.COMPLETED, "ANDROID", now, null, "UTC", null, 60, null, null, now, now, null)));
        when(transcripts.findBySession(sessionId)).thenReturn(java.util.Arrays.stream(speakers)
                .map(s -> new TranscriptChunk(UUID.randomUUID(), sessionId, 0, null, null, s, "text", null, now))
                .toList());
    }

    @Test
    void choosingASpeakerMarksTheirMemories() {
        conversationWithSpeakers("Speaker 1", "Speaker 2");
        service.setSelfSpeaker(sessionId, userId, " Speaker 2 ");
        verify(repository).updateSelfSpeaker(sessionId, "Speaker 2");
        verify(memories).updateOwnerIsSelf(sessionId, userId, "Speaker 2");
    }

    @Test
    void aSpeakerWhoIsNotInTheConversationIsRejected() {
        conversationWithSpeakers("Speaker 1", "Speaker 2");
        assertThrows(IllegalArgumentException.class, () -> service.setSelfSpeaker(sessionId, userId, "Speaker 7"));
        verify(repository, never()).updateSelfSpeaker(any(), any());
        verify(memories, never()).updateOwnerIsSelf(any(), any(), any());
    }

    @Test
    void anotherUsersConversationIsNotFound() {
        when(repository.findByIdAndUser(sessionId, userId)).thenReturn(Optional.empty());
        assertThrows(com.secondmemory.common.NotFoundException.class,
                () -> service.setSelfSpeaker(sessionId, userId, "Speaker 1"));
        verify(transcripts, never()).findBySession(any());
    }
}
