package com.secondmemory.auth;

import com.secondmemory.audio.AudioStorageService;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.UUID;

@Service
public class AccountService {
    private final AccountRepository accounts;
    private final AudioStorageService audioStorage;

    public AccountService(AccountRepository accounts, AudioStorageService audioStorage) {
        this.accounts = accounts;
        this.audioStorage = audioStorage;
    }

    /**
     * Permanently deletes the account and its data. Stored audio goes first: if storage fails, the
     * request fails with the account still intact and can be retried, instead of leaving recordings
     * behind that no database row points to any more.
     */
    public void deleteAccount(UUID userId) throws IOException {
        for (String audioUri : accounts.audioUris(userId)) {
            audioStorage.delete(audioUri);
        }
        accounts.deleteUserRows(userId);
    }
}
