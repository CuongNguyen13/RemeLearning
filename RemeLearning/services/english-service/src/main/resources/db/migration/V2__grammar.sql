-- Grammar domain schema for english-service (reme_english).
-- Only weak points with category = "grammar" from learning.gap.analyzed are stored here;
-- reuses the shared transcripts/transcript_segments tables from V1.

CREATE TABLE grammar_weak_points (
    id BIGSERIAL PRIMARY KEY,
    recording_id VARCHAR(100) NOT NULL,
    user_id VARCHAR(100) NOT NULL,
    item_id VARCHAR(100) NOT NULL,
    label VARCHAR(255) NOT NULL,
    grammar_type VARCHAR(30) NOT NULL,
    forgetting_score DOUBLE PRECISION NOT NULL,
    recommendation TEXT NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_grammar_weak_points_user_item UNIQUE (user_id, item_id)
);

CREATE INDEX idx_grammar_weak_points_user_id ON grammar_weak_points (user_id);
CREATE INDEX idx_grammar_weak_points_type ON grammar_weak_points (grammar_type);
