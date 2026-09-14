CREATE TABLE memory_session (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL,
    title VARCHAR(255),
    status VARCHAR(30) NOT NULL,
    source VARCHAR(30) NOT NULL DEFAULT 'ANDROID',
    started_at TIMESTAMPTZ NOT NULL,
    ended_at TIMESTAMPTZ,
    timezone VARCHAR(100),
    audio_uri TEXT,
    duration_seconds INTEGER,
    summary TEXT,
    error_code VARCHAR(100),
    error_message TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_memory_session_user_started
    ON memory_session(user_id, started_at DESC);
