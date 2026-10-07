package com.secondmemory.auth;

import com.secondmemory.audio.AudioStorageService;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AccountServiceTest {
    private final UUID userId = UUID.randomUUID();
    private final AccountRepository accounts = mock(AccountRepository.class);
    private final AudioStorageService audioStorage = mock(AudioStorageService.class);
    private final AccountService service = new AccountService(accounts, audioStorage);

    @Test
    void deletesStoredAudioBeforeTheRows() throws IOException {
        when(accounts.audioUris(userId)).thenReturn(List.of("s3://bucket/a.m4a", "s3://bucket/b.m4a"));
        service.deleteAccount(userId);
        var order = inOrder(audioStorage, accounts);
        order.verify(audioStorage).delete("s3://bucket/a.m4a");
        order.verify(audioStorage).delete("s3://bucket/b.m4a");
        order.verify(accounts).deleteUserRows(userId);
    }

    @Test
    void storageFailureLeavesTheAccountIntact() throws IOException {
        when(accounts.audioUris(userId)).thenReturn(List.of("s3://bucket/a.m4a"));
        doThrow(new IOException("S3 down")).when(audioStorage).delete("s3://bucket/a.m4a");
        assertThrows(IOException.class, () -> service.deleteAccount(userId));
        verify(accounts, never()).deleteUserRows(any());
    }
}
