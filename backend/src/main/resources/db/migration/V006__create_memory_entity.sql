CREATE TABLE memory_entity (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL,
    type VARCHAR(30) NOT NULL,
    canonical_name VARCHAR(255) NOT NULL,
    aliases JSONB NOT NULL DEFAULT '[]'::jsonb,
    metadata JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE(user_id, type, canonical_name)
);

CREATE TABLE memory_entity_link (
    memory_id UUID NOT NULL REFERENCES memory(id) ON DELETE CASCADE,
    entity_id UUID NOT NULL REFERENCES memory_entity(id) ON DELETE CASCADE,
    relation VARCHAR(50),
    PRIMARY KEY (memory_id, entity_id)
);
