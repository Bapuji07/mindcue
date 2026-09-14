CREATE TABLE transcript_chunk (
    id UUID PRIMARY KEY,
    session_id UUID NOT NULL REFERENCES memory_session(id) ON DELETE CASCADE,
    sequence_no INTEGER NOT NULL,
    start_ms BIGINT,
    end_ms BIGINT,
    speaker_label VARCHAR(100),
    text TEXT NOT NULL,
    confidence NUMERIC(5,4),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE(session_id, sequence_no)
);

CREATE INDEX idx_transcript_chunk_session_sequence
    ON transcript_chunk(session_id, sequence_no);
