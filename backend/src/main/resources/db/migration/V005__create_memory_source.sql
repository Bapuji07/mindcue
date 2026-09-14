CREATE TABLE memory_source (
    memory_id UUID NOT NULL REFERENCES memory(id) ON DELETE CASCADE,
    transcript_chunk_id UUID NOT NULL REFERENCES transcript_chunk(id) ON DELETE CASCADE,
    PRIMARY KEY (memory_id, transcript_chunk_id)
);
