package com.secondmemory.memory;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public class MemoryRepository {
    private final JdbcTemplate jdbc;

    public MemoryRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public UUID insert(UUID userId,
                       UUID sessionId,
                       MemoryType type,
                       String title,
                       String content,
                       java.math.BigDecimal importance,
                       java.math.BigDecimal confidence,
                       ResolutionStatus resolutionStatus,
                       Instant occurredAt,
                       Instant dueAt,
                       String aiProvider,
                       String aiModel,
                       String promptVersion) {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO memory
                    (id, user_id, session_id, type, title, content, importance, confidence,
                     resolution_status, occurred_at, due_at, ai_provider, ai_model,
                     extraction_version, prompt_version)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                id, userId, sessionId, type.name(), title, content,
                importance, confidence, resolutionStatus.name(),
                occurredAt == null ? null : Timestamp.from(occurredAt),
                dueAt == null ? null : Timestamp.from(dueAt),
                aiProvider, aiModel, "v1", promptVersion);
        return id;
    }

    public void linkSource(UUID memoryId, UUID chunkId) {
        jdbc.update("""
                INSERT INTO memory_source(memory_id, transcript_chunk_id)
                VALUES (?, ?)
                ON CONFLICT DO NOTHING
                """, memoryId, chunkId);
    }

    public List<MemoryRecord> list(UUID userId, MemoryType type, int limit) {
        StringBuilder sql = new StringBuilder("SELECT * FROM memory WHERE user_id = ? AND is_active = TRUE");
        if (type != null) {
            sql.append(" AND type = '").append(type.name()).append("'");
        }
        sql.append(" ORDER BY created_at DESC LIMIT ?");
        return jdbc.query(sql.toString(), (rs, rowNum) -> new MemoryRecord(
                rs.getObject("id", UUID.class),
                rs.getObject("user_id", UUID.class),
                rs.getObject("session_id", UUID.class),
                MemoryType.valueOf(rs.getString("type")),
                rs.getString("title"),
                rs.getString("content"),
                rs.getBigDecimal("importance"),
                rs.getBigDecimal("confidence"),
                ResolutionStatus.valueOf(rs.getString("resolution_status")),
                timestamp(rs.getTimestamp("occurred_at")),
                timestamp(rs.getTimestamp("due_at")),
                rs.getString("ai_provider"),
                rs.getString("ai_model"),
                rs.getString("prompt_version"),
                rs.getBoolean("is_active"),
                rs.getTimestamp("created_at").toInstant(),
                rs.getTimestamp("updated_at").toInstant()
        ), userId, limit);
    }

    public List<MemoryRecord> listBySession(UUID sessionId, UUID userId) {
        return jdbc.query("""
                SELECT * FROM memory
                WHERE session_id = ? AND user_id = ? AND is_active = TRUE
                ORDER BY created_at
                """, (rs, rowNum) -> mapRow(rs), sessionId, userId);
    }

    public Optional<MemoryRecord> findByIdAndUser(UUID id, UUID userId) {
        return jdbc.query("SELECT * FROM memory WHERE id = ? AND user_id = ?", (rs, rowNum) -> mapRow(rs), id, userId)
                .stream().findFirst();
    }

    public void update(UUID id, UUID userId, UpdateMemoryRequest request) {
        MemoryRecord current = findByIdAndUser(id, userId).orElseThrow();
        String title = request.title() == null ? current.title() : request.title().trim();
        String content = request.content() == null ? current.content() : request.content().trim();
        ResolutionStatus status = request.resolutionStatus() == null
                ? current.resolutionStatus() : request.resolutionStatus();
        boolean active = request.active() == null ? current.active() : request.active();
        if (title.isBlank()) throw new IllegalArgumentException("Memory title must not be blank");
        if (content.isBlank()) throw new IllegalArgumentException("Memory content must not be blank");
        jdbc.update("""
                UPDATE memory
                SET title = ?, content = ?, resolution_status = ?, is_active = ?, updated_at = NOW()
                WHERE id = ? AND user_id = ?
                """, title, content, status.name(), active, id, userId);
    }

    public void deleteBySession(UUID sessionId, UUID userId) {
        jdbc.update("DELETE FROM memory WHERE session_id = ? AND user_id = ?", sessionId, userId);
    }


    public void updateEmbedding(UUID memoryId, float[] embedding) {
        jdbc.update("UPDATE memory SET embedding = CAST(? AS vector), updated_at = NOW() WHERE id = ?",
                toVectorLiteral(embedding), memoryId);
    }

    public List<MemorySearchHit> searchSimilar(UUID userId, float[] queryEmbedding, int limit) {
        String vector = toVectorLiteral(queryEmbedding);
        return jdbc.query("""
                SELECT *, 1 - (embedding <=> CAST(? AS vector)) AS similarity
                FROM memory
                WHERE user_id = ?
                  AND is_active = TRUE
                  AND embedding IS NOT NULL
                ORDER BY embedding <=> CAST(? AS vector)
                LIMIT ?
                """, (rs, rowNum) -> new MemorySearchHit(
                new MemoryRecord(
                        rs.getObject("id", UUID.class),
                        rs.getObject("user_id", UUID.class),
                        rs.getObject("session_id", UUID.class),
                        MemoryType.valueOf(rs.getString("type")),
                        rs.getString("title"),
                        rs.getString("content"),
                        rs.getBigDecimal("importance"),
                        rs.getBigDecimal("confidence"),
                        ResolutionStatus.valueOf(rs.getString("resolution_status")),
                        timestamp(rs.getTimestamp("occurred_at")),
                        timestamp(rs.getTimestamp("due_at")),
                        rs.getString("ai_provider"),
                        rs.getString("ai_model"),
                        rs.getString("prompt_version"),
                        rs.getBoolean("is_active"),
                        rs.getTimestamp("created_at").toInstant(),
                        rs.getTimestamp("updated_at").toInstant()
                ),
                rs.getDouble("similarity")
        ), vector, userId, vector, limit);
    }

    public List<MemorySourceEvidence> sourcesForMemory(UUID memoryId) {
        return jdbc.query("""
                SELECT ms.memory_id, tc.session_id, tc.id AS transcript_chunk_id,
                       tc.sequence_no, tc.speaker_label, tc.text
                FROM memory_source ms
                JOIN transcript_chunk tc ON tc.id = ms.transcript_chunk_id
                WHERE ms.memory_id = ?
                ORDER BY tc.sequence_no
                """, (rs, rowNum) -> new MemorySourceEvidence(
                rs.getObject("memory_id", UUID.class),
                rs.getObject("session_id", UUID.class),
                rs.getObject("transcript_chunk_id", UUID.class),
                rs.getInt("sequence_no"),
                rs.getString("speaker_label"),
                rs.getString("text")
        ), memoryId);
    }

    private String toVectorLiteral(float[] values) {
        StringBuilder builder = new StringBuilder("[");
        for (int i = 0; i < values.length; i++) {
            if (i > 0) builder.append(',');
            builder.append(values[i]);
        }
        return builder.append(']').toString();
    }

    private static Instant timestamp(Timestamp value) {
        return value == null ? null : value.toInstant();
    }

    private MemoryRecord mapRow(java.sql.ResultSet rs) throws java.sql.SQLException {
        return new MemoryRecord(
                rs.getObject("id", UUID.class),
                rs.getObject("user_id", UUID.class),
                rs.getObject("session_id", UUID.class),
                MemoryType.valueOf(rs.getString("type")),
                rs.getString("title"),
                rs.getString("content"),
                rs.getBigDecimal("importance"),
                rs.getBigDecimal("confidence"),
                ResolutionStatus.valueOf(rs.getString("resolution_status")),
                timestamp(rs.getTimestamp("occurred_at")),
                timestamp(rs.getTimestamp("due_at")),
                rs.getString("ai_provider"),
                rs.getString("ai_model"),
                rs.getString("prompt_version"),
                rs.getBoolean("is_active"),
                rs.getTimestamp("created_at").toInstant(),
                rs.getTimestamp("updated_at").toInstant()
        );
    }
}
