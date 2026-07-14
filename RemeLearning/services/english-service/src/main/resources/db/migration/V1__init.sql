-- Initial schema for english-service (reme_english) - vocabulary domain.
-- Grammar and pronunciation domains have no tables yet; add their own V__ migrations
-- under this same db/migration folder once those modules gain real persistence.

CREATE TABLE transcripts (
    id BIGSERIAL PRIMARY KEY,
    recording_id VARCHAR(100) NOT NULL UNIQUE,
    user_id VARCHAR(100) NOT NULL,
    full_text TEXT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_transcripts_user_id ON transcripts (user_id);

CREATE TABLE transcript_segments (
    id BIGSERIAL PRIMARY KEY,
    transcript_id BIGINT NOT NULL REFERENCES transcripts (id) ON DELETE CASCADE,
    speaker VARCHAR(50) NOT NULL,
    content TEXT NOT NULL,
    start_seconds DOUBLE PRECISION NOT NULL,
    end_seconds DOUBLE PRECISION NOT NULL,
    segment_order INT NOT NULL
);

CREATE INDEX idx_transcript_segments_transcript_id ON transcript_segments (transcript_id);

-- Only weak points with category = "vocabulary" from learning.gap.analyzed are stored here;
-- grammar/pronunciation categories belong to their own tables in this same service once
-- those domains are implemented.
CREATE TABLE vocabulary_weak_points (
    id BIGSERIAL PRIMARY KEY,
    recording_id VARCHAR(100) NOT NULL,
    user_id VARCHAR(100) NOT NULL,
    item_id VARCHAR(100) NOT NULL,
    label VARCHAR(255) NOT NULL,
    vocabulary_type VARCHAR(30) NOT NULL,
    forgetting_score DOUBLE PRECISION NOT NULL,
    recommendation TEXT NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_vocabulary_weak_points_user_item UNIQUE (user_id, item_id)
);

CREATE INDEX idx_vocabulary_weak_points_user_id ON vocabulary_weak_points (user_id);
CREATE INDEX idx_vocabulary_weak_points_type ON vocabulary_weak_points (vocabulary_type);
