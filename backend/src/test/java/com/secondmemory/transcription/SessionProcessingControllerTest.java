package com.secondmemory.transcription;

import com.secondmemory.common.NotFoundException;
import com.secondmemory.session.MemorySessionRepository;
import com.secondmemory.session.MemorySessionService;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
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
    private final SessionProcessingController controller = new SessionProcessingController(
            mock(TranscriptionService.class), sessions, sessionRepository, orchestrator);

    @Test
    void claimedSessionStartsProcessing() {
        when(sessionRepository.claimForProcessing(sessionId, userId)).thenReturn(true);
        var response = controller.process(sessionId, auth);
        assertEquals(HttpStatus.ACCEPTED, response.getStatusCode());
        verify(orchestrator).runProcessing(sessionId, userId);
    }

    @Test
    void activeOrCompletedSessionIsConflictAndNotReprocessed() {
        when(sessionRepository.claimForProcessing(sessionId, userId)).thenReturn(false);
        var ex = assertThrows(ResponseStatusException.class, () -> controller.process(sessionId, auth));
        assertEquals(HttpStatus.CONFLICT, ex.getStatusCode());
        verify(orchestrator, never()).runProcessing(any(), any());
    }

    @Test
    void unknownSessionIsNotFoundBeforeClaiming() {
        when(sessions.get(sessionId, userId)).thenThrow(new NotFoundException("Memory session not found"));
        assertThrows(NotFoundException.class, () -> controller.process(sessionId, auth));
        verify(sessionRepository, never()).claimForProcessing(any(), any());
    }
}
