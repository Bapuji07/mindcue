CREATE TABLE ai_execution (
    id UUID PRIMARY KEY,
    user_id UUID,
    session_id UUID REFERENCES memory_session(id) ON DELETE SET NULL,
    purpose VARCHAR(50) NOT NULL,
    provider VARCHAR(50) NOT NULL,
    model VARCHAR(150),
    prompt_version VARCHAR(100),
    input_tokens BIGINT,
    output_tokens BIGINT,
    latency_ms BIGINT,
    status VARCHAR(30) NOT NULL,
    error_message TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
