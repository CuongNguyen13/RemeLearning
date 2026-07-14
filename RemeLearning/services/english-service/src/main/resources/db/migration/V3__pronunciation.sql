-- Pronunciation domain schema for english-service (reme_english).
-- Only weak points with category = "pronunciation" from learning.gap.analyzed are stored here;
-- reuses the shared transcripts/transcript_segments tables from V1.

CREATE TABLE pronunciation_weak_points (
    id BIGSERIAL PRIMARY KEY,
    recording_id VARCHAR(100) NOT NULL,
    user_id VARCHAR(100) NOT NULL,
    item_id VARCHAR(100) NOT NULL,
    label VARCHAR(255) NOT NULL,
    pronunciation_type VARCHAR(30) NOT NULL,
    forgetting_score DOUBLE PRECISION NOT NULL,
    recommendation TEXT NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_pronunciation_weak_points_user_item UNIQUE (user_id, item_id)
);

CREATE INDEX idx_pronunciation_weak_points_user_id ON pronunciation_weak_points (user_id);
CREATE INDEX idx_pronunciation_weak_points_type ON pronunciation_weak_points (pronunciation_type);
