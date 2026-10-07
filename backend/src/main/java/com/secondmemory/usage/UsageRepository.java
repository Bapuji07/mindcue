package com.secondmemory.usage;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;

@Repository
public class UsageRepository {
    private final JdbcTemplate jdbc;

    public UsageRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** Locks the user's row for the current transaction so their usage checks run one at a time. */
    public boolean lockUserAndCheckUnlimited(UUID userId) {
        return jdbc.queryForList("SELECT unlimited_usage FROM app_user WHERE id = ? FOR UPDATE", Boolean.class, userId)
                .stream().findFirst().orElse(false);
    }

    public boolean isUnlimited(UUID userId) {
        return jdbc.queryForList("SELECT unlimited_usage FROM app_user WHERE id = ?", Boolean.class, userId)
                .stream().findFirst().orElse(false);
    }

    public boolean hasAudioCharge(UUID sessionId) {
        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM usage_event WHERE session_id = ? AND kind = 'AUDIO_SECONDS'",
                Integer.class, sessionId);
        return count != null && count > 0;
    }

    public long sumSince(UUID userId, UsageKind kind, Instant since) {
        Long sum = jdbc.queryForObject("""
                SELECT COALESCE(SUM(amount), 0) FROM usage_event
                WHERE user_id = ? AND kind = ? AND created_at >= ?
                """, Long.class, userId, kind.name(), Timestamp.from(since));
        return sum == null ? 0 : sum;
    }

    public long globalSumSince(UsageKind kind, Instant since) {
        Long sum = jdbc.queryForObject(
                "SELECT COALESCE(SUM(amount), 0) FROM usage_event WHERE kind = ? AND created_at >= ?",
                Long.class, kind.name(), Timestamp.from(since));
        return sum == null ? 0 : sum;
    }

    public UUID insert(UUID userId, UUID sessionId, UsageKind kind, int amount) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO usage_event (id, user_id, session_id, kind, amount) VALUES (?, ?, ?, ?, ?)",
                id, userId, sessionId, kind.name(), amount);
        return id;
    }

    public void delete(UUID id) {
        jdbc.update("DELETE FROM usage_event WHERE id = ?", id);
    }
}
