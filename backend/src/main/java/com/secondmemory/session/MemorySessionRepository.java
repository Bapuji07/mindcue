package com.secondmemory.session;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Optional;
import java.util.List;
import java.util.UUID;

@Repository
public class MemorySessionRepository {
    private final JdbcTemplate jdbc;

    public MemorySessionRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public MemorySession create(UUID userId, CreateSessionRequest request) {
        UUID id = UUID.randomUUID();
        String source = request.source() == null || request.source().isBlank() ? "ANDROID" : request.source();
        jdbc.update("""
                INSERT INTO memory_session
                    (id, user_id, title, status, source, started_at, timezone)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                """,
                id,
                userId,
                request.title(),
                SessionStatus.RECORDING.name(),
                source,
                Timestamp.from(request.startedAt().toInstant()),
                request.timezone());
        return findById(id).orElseThrow();
    }

    public Optional<MemorySession> findById(UUID id) {
        return jdbc.query("SELECT * FROM memory_session WHERE id = ?", this::mapRow, id)
                .stream().findFirst();
    }

    public Optional<MemorySession> findByIdAndUser(UUID id, UUID userId) {
        return jdbc.query("SELECT * FROM memory_session WHERE id = ? AND user_id = ?", this::mapRow, id, userId)
                .stream().findFirst();
    }

    public List<MemorySession> listByUser(UUID userId, int limit) {
        return jdbc.query("""
                SELECT * FROM memory_session
                WHERE user_id = ?
                ORDER BY started_at DESC
                LIMIT ?
                """, this::mapRow, userId, limit);
    }

    public void updateTitle(UUID id, UUID userId, String title) {
        jdbc.update("UPDATE memory_session SET title = ?, updated_at = NOW() WHERE id = ? AND user_id = ?",
                title, id, userId);
    }

    public void delete(UUID id, UUID userId) {
        jdbc.update("DELETE FROM memory_session WHERE id = ? AND user_id = ?", id, userId);
    }

    public void markAudioReceived(UUID id, String audioUri, int durationSeconds) {
        jdbc.update("""
                UPDATE memory_session
                SET audio_uri = ?, duration_seconds = ?, status = ?, updated_at = NOW()
                WHERE id = ?
                """, audioUri, durationSeconds, SessionStatus.AUDIO_RECEIVED.name(), id);
    }

    public void finish(UUID id, Instant endedAt, Integer durationSeconds) {
        jdbc.update("""
                UPDATE memory_session
                SET ended_at = ?, duration_seconds = ?, updated_at = NOW()
                WHERE id = ?
                """,
                endedAt == null ? null : Timestamp.from(endedAt),
                durationSeconds,
                id);
    }

    /**
     * Atomically moves a session into PROCESSING if nothing is working on it: it has audio or a
     * transcript waiting, it failed, or an in-progress run has not moved for 10 minutes (e.g. the
     * server restarted mid-run). Returns false when a run is already active or the session is
     * already COMPLETED, or the session has used up its {@code maxAttempts}. Each successful claim
     * counts one attempt. Concurrent callers serialize on the row, so only one of them wins.
     */
    public boolean claimForProcessing(UUID id, UUID userId, int maxAttempts) {
        return jdbc.update("""
                UPDATE memory_session
                SET status = 'PROCESSING', error_message = NULL, updated_at = NOW(),
                    processing_attempts = processing_attempts + 1
                WHERE id = ? AND user_id = ? AND processing_attempts < ?
                  AND (status IN ('AUDIO_RECEIVED', 'TRANSCRIPTION_COMPLETE', 'FAILED')
                       OR (status IN ('TRANSCRIBING', 'PROCESSING')
                           AND updated_at < NOW() - INTERVAL '10 minutes'))
                """, id, userId, maxAttempts) == 1;
    }

    public int processingAttempts(UUID id) {
        Integer attempts = jdbc.queryForObject(
                "SELECT processing_attempts FROM memory_session WHERE id = ?", Integer.class, id);
        return attempts == null ? 0 : attempts;
    }

    public void clearAudioUri(UUID id) {
        jdbc.update("UPDATE memory_session SET audio_uri = NULL, updated_at = NOW() WHERE id = ?", id);
    }

    public void updateStatus(UUID id, SessionStatus status) {
        jdbc.update("UPDATE memory_session SET status = ?, updated_at = NOW() WHERE id = ?",
                status.name(), id);
    }

    public void updateSummary(UUID id, String summary, SessionStatus status) {
        jdbc.update("""
                UPDATE memory_session
                SET summary = ?, status = ?, error_message = NULL, updated_at = NOW()
                WHERE id = ?
                """, summary, status.name(), id);
    }

    public void markFailed(UUID id, String errorMessage) {
        jdbc.update("""
                UPDATE memory_session
                SET status = ?, error_message = ?, updated_at = NOW()
                WHERE id = ?
                """, SessionStatus.FAILED.name(), errorMessage, id);
    }

    private MemorySession mapRow(ResultSet rs, int rowNum) throws SQLException {
        return new MemorySession(
                rs.getObject("id", UUID.class),
                rs.getObject("user_id", UUID.class),
                rs.getString("title"),
                SessionStatus.valueOf(rs.getString("status")),
                rs.getString("source"),
                instant(rs, "started_at"),
                instant(rs, "ended_at"),
                rs.getString("timezone"),
                rs.getString("audio_uri"),
                (Integer) rs.getObject("duration_seconds"),
                rs.getString("summary"),
                rs.getString("error_message"),
                instant(rs, "created_at"),
                instant(rs, "updated_at")
        );
    }

    private Instant instant(ResultSet rs, String column) throws SQLException {
        Timestamp value = rs.getTimestamp(column);
        return value == null ? null : value.toInstant();
    }
}
