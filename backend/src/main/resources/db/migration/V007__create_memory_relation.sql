CREATE TABLE memory_relation (
    id UUID PRIMARY KEY,
    source_memory_id UUID NOT NULL REFERENCES memory(id) ON DELETE CASCADE,
    target_memory_id UUID NOT NULL REFERENCES memory(id) ON DELETE CASCADE,
    relation_type VARCHAR(50) NOT NULL,
    confidence NUMERIC(3,2),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CHECK (source_memory_id <> target_memory_id)
);
