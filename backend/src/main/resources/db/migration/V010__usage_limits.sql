ALTER TABLE app_user ADD COLUMN unlimited_usage BOOLEAN NOT NULL DEFAULT FALSE;

ALTER TABLE memory_session ADD COLUMN processing_attempts INT NOT NULL DEFAULT 0;

-- Metered usage. Rows outlive their session (SET NULL) so deleting a conversation never refunds quota.
CREATE TABLE usage_event (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL,
    session_id UUID REFERENCES memory_session(id) ON DELETE SET NULL,
    kind VARCHAR(30) NOT NULL,
    amount INT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_usage_event_user_kind_time ON usage_event (user_id, kind, created_at);
CREATE INDEX idx_usage_event_kind_time ON usage_event (kind, created_at);
-- A session's audio is charged at most once, however often its processing is retried.
CREATE UNIQUE INDEX uq_usage_event_session_audio ON usage_event (session_id) WHERE kind = 'AUDIO_SECONDS';
