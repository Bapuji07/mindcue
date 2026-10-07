package com.secondmemory.auth;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Repository
public class AccountRepository {
    /** Owner of usage rows kept after an account is deleted: the counts stay, the person is gone. */
    static final UUID ANONYMOUS_USER = new UUID(0, 0);

    private final JdbcTemplate jdbc;

    public AccountRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** Stored recordings still referenced by the user's conversations (unprocessed or failed ones). */
    public List<String> audioUris(UUID userId) {
        return jdbc.queryForList(
                "SELECT audio_uri FROM memory_session WHERE user_id = ? AND audio_uri IS NOT NULL",
                String.class, userId);
    }

    /**
     * Removes everything the user owns in one transaction. Child rows (memory sources, entity links,
     * relations, transcript chunks) go with their parents through ON DELETE CASCADE. Usage rows are
     * re-assigned to an anonymous owner so the global daily cap stays accurate.
     */
    @Transactional
    public void deleteUserRows(UUID userId) {
        jdbc.update("DELETE FROM memory WHERE user_id = ?", userId);
        jdbc.update("DELETE FROM memory_entity WHERE user_id = ?", userId);
        jdbc.update("DELETE FROM memory_session WHERE user_id = ?", userId);
        jdbc.update("DELETE FROM ai_execution WHERE user_id = ?", userId);
        jdbc.update("UPDATE usage_event SET user_id = ? WHERE user_id = ?", ANONYMOUS_USER, userId);
        jdbc.update("DELETE FROM app_user WHERE id = ?", userId);
    }
}
