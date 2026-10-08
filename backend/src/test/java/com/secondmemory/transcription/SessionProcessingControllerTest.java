package com.secondmemory.transcription;

import com.secondmemory.common.LimitExceededException;
import com.secondmemory.common.NotFoundException;
import com.secondmemory.config.LimitsProperties;
import com.secondmemory.session.MemorySession;
import com.secondmemory.session.MemorySessionRepository;
import com.secondmemory.session.MemorySessionService;
import com.secondmemory.session.SessionStatus;
import com.secondmemory.usage.UsageService;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SessionProcessingControllerTest {
    private final UUID sessionId = UUID.randomUUID();
    private final UUID userId = UUID.randomUUID();
    private final Authentication auth = new UsernamePasswordAuthenticationToken(userId.toString(), null, List.of());
    private final MemorySessionService sessions = mock(MemorySessionService.class);
    private final MemorySessionRepository sessionRepository = mock(MemorySessionRepository.class);
    private final SessionProcessingOrchestrator orchestrator = mock(SessionProcessingOrchestrator.class);
    private final UsageService usage = mock(UsageService.class);
    private final SessionProcessingController controller = new SessionProcessingController(
            sessions, sessionRepository, orchestrator, usage, LimitsProperties.defaults());

    private MemorySession session(String audioUri, Integer seconds) {
        Instant now = Instant.now();
        return new MemorySession(sessionId, userId, "t", SessionStatus.AUDIO_RECEIVED, "ANDROID", now, null, "UTC",
                audioUri, seconds, null, null, now, now, null);
    }

    @Test
    void claimedSessionIsChargedThenStartsProcessing() {
        when(sessions.get(sessionId, userId)).thenReturn(session("s3://b/k.m4a", 90));
        when(sessionRepository.claimForProcessing(sessionId, userId, 3)).thenReturn(true);
        var response = controller.process(sessionId, auth);
        assertEquals(HttpStatus.ACCEPTED, response.getStatusCode());
        var order = inOrder(usage, sessionRepository, orchestrator);
        order.verify(usage).chargeAudio(userId, sessionId, 90);
        order.verify(sessionRepository).claimForProcessing(sessionId, userId, 3);
        order.verify(orchestrator).runProcessing(sessionId, userId);
    }

    @Test
    void transcribedSessionIsNotChargedAgain() {
        when(sessions.get(sessionId, userId)).thenReturn(session(null, 90));
        when(sessionRepository.claimForProcessing(sessionId, userId, 3)).thenReturn(true);
        controller.process(sessionId, auth);
        verify(usage, never()).chargeAudio(any(), any(), anyInt());
    }

    @Test
    void limitReachedStopsBeforeClaimingOrProcessing() {
        when(sessions.get(sessionId, userId)).thenReturn(session("s3://b/k.m4a", 90));
        doThrow(new LimitExceededException("limit", null)).when(usage).chargeAudio(userId, sessionId, 90);
        assertThrows(LimitExceededException.class, () -> controller.process(sessionId, auth));
        verify(sessionRepository, never()).claimForProcessing(any(), any(), anyInt());
        verify(orchestrator, never()).runProcessing(any(), any());
    }

    @Test
    void exhaustedRetriesAreRejected() {
        when(sessions.get(sessionId, userId)).thenReturn(session(null, 90));
        when(sessionRepository.processingAttempts(sessionId)).thenReturn(3);
        assertThrows(LimitExceededException.class, () -> controller.process(sessionId, auth));
        verify(orchestrator, never()).runProcessing(any(), any());
    }

    @Test
    void activeOrCompletedSessionIsConflictAndNotReprocessed() {
        when(sessions.get(sessionId, userId)).thenReturn(session(null, 90));
        when(sessionRepository.claimForProcessing(sessionId, userId, 3)).thenReturn(false);
        var ex = assertThrows(ResponseStatusException.class, () -> controller.process(sessionId, auth));
        assertEquals(HttpStatus.CONFLICT, ex.getStatusCode());
        verify(orchestrator, never()).runProcessing(any(), any());
    }

    @Test
    void unknownSessionIsNotFoundBeforeClaiming() {
        when(sessions.get(sessionId, userId)).thenThrow(new NotFoundException("Memory session not found"));
        assertThrows(NotFoundException.class, () -> controller.process(sessionId, auth));
        verify(sessionRepository, never()).claimForProcessing(any(), any(), anyInt());
    }
}
