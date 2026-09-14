package com.secondmemory.transcript;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public class TranscriptRepository {
    private final JdbcTemplate jdbc;

    public TranscriptRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public TranscriptChunk create(UUID sessionId, CreateTranscriptChunkRequest request) {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO transcript_chunk
                    (id, session_id, sequence_no, start_ms, end_ms, speaker_label, text, confidence)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                """, id, sessionId, request.sequenceNo(), request.startMs(), request.endMs(),
                request.speakerLabel(), request.text(), request.confidence());
        return findBySession(sessionId).stream()
                .filter(chunk -> chunk.id().equals(id))
                .findFirst().orElseThrow();
    }

    public List<TranscriptChunk> findBySession(UUID sessionId) {
        return jdbc.query("""
                SELECT * FROM transcript_chunk
                WHERE session_id = ?
                ORDER BY sequence_no
                """, (rs, rowNum) -> new TranscriptChunk(
                rs.getObject("id", UUID.class),
                rs.getObject("session_id", UUID.class),
                rs.getInt("sequence_no"),
                (Long) rs.getObject("start_ms"),
                (Long) rs.getObject("end_ms"),
                rs.getString("speaker_label"),
                rs.getString("text"),
                rs.getBigDecimal("confidence"),
                rs.getTimestamp("created_at").toInstant()
        ), sessionId);
    }
    public void deleteBySession(UUID sessionId) {
        jdbc.update("DELETE FROM transcript_chunk WHERE session_id = ?", sessionId);
    }

}
