CREATE TABLE memory (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL,
    session_id UUID REFERENCES memory_session(id) ON DELETE SET NULL,
    type VARCHAR(30) NOT NULL,
    title VARCHAR(500),
    content TEXT NOT NULL,
    importance NUMERIC(3,2) NOT NULL DEFAULT 0.50,
    confidence NUMERIC(3,2) NOT NULL DEFAULT 1.00,
    resolution_status VARCHAR(30) NOT NULL DEFAULT 'NONE',
    occurred_at TIMESTAMPTZ,
    due_at TIMESTAMPTZ,
    metadata JSONB NOT NULL DEFAULT '{}'::jsonb,
    embedding JSONB,
    ai_provider VARCHAR(50),
    ai_model VARCHAR(150),
    extraction_version VARCHAR(50),
    prompt_version VARCHAR(100),
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_memory_user_created
    ON memory(user_id, created_at DESC);
CREATE INDEX idx_memory_user_type
    ON memory(user_id, type);
CREATE INDEX idx_memory_open_items
    ON memory(user_id, resolution_status, due_at)
    WHERE is_active = TRUE;
