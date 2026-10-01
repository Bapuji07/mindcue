package com.secondmemory.memory;

import com.secondmemory.common.NotFoundException;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MemoryControllerTest {
    private final UUID userId = UUID.randomUUID();
    private final Authentication auth = new UsernamePasswordAuthenticationToken(userId.toString(), null, List.of());
    private final MemoryRepository repository = mock(MemoryRepository.class);
    private final MemoryController controller =
            new MemoryController(repository, mock(MemoryExtractionService.class), mock(MemoryAnswerService.class));

    @Test
    void deleteUnknownMemoryIsNotFound() {
        UUID id = UUID.randomUUID();
        when(repository.delete(id, userId)).thenReturn(false);
        assertThrows(NotFoundException.class, () -> controller.delete(id, auth));
    }

    @Test
    void deleteScopesToCurrentUser() {
        UUID id = UUID.randomUUID();
        when(repository.delete(id, userId)).thenReturn(true);
        controller.delete(id, auth);
        verify(repository).delete(id, userId);
    }

    @Test
    void getUnknownMemoryIsNotFound() {
        UUID id = UUID.randomUUID();
        when(repository.findByIdAndUser(id, userId)).thenReturn(Optional.empty());
        assertThrows(NotFoundException.class, () -> controller.get(id, auth));
    }

    @Test
    void listClampsLimitAndPassesFilters() {
        controller.list(MemoryType.TASK, ResolutionStatus.OPEN, "milk", 10_000, auth);
        verify(repository).search(userId, MemoryType.TASK, ResolutionStatus.OPEN, "milk", 500);
    }

    @Test
    void openPassesOverdueFlag() {
        controller.open(true, 0, auth);
        verify(repository).listOpen(userId, true, 1);
    }

    @Test
    void likeWildcardsAreEscaped() {
        assertEquals("100\\% \\_done\\\\", MemoryRepository.escapeLike("100% _done\\"));
    }
}
