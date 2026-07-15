-- Practice/redo-exercise domain for english-service (reme_english).
-- Tracks recurring-mistake history (occurrence count + recency) across all three domains
-- (vocabulary/grammar/pronunciation) and an audit log of every graded redo attempt, so a
-- learner's mistake history can be bundled into a learning.gap.analysis.requested event
-- and re-scored by ai-service's RuleBasedAnalyzer.

CREATE TABLE mistake_history (
    id BIGSERIAL PRIMARY KEY,
    user_id VARCHAR(100) NOT NULL,
    item_id VARCHAR(100) NOT NULL,
    category VARCHAR(30) NOT NULL,
    label VARCHAR(255) NOT NULL,
    occurrence_count INT NOT NULL DEFAULT 1,
    last_seen_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_mistake_history_user_item UNIQUE (user_id, item_id)
);

CREATE INDEX idx_mistake_history_user_id ON mistake_history (user_id);

-- Audit log of every graded redo-exercise answer; not read back by the scoring pipeline,
-- kept for traceability of what a learner actually answered.
CREATE TABLE practice_attempts (
    id BIGSERIAL PRIMARY KEY,
    user_id VARCHAR(100) NOT NULL,
    item_id VARCHAR(100) NOT NULL,
    category VARCHAR(30) NOT NULL,
    label VARCHAR(255) NOT NULL,
    is_correct BOOLEAN NOT NULL,
    attempted_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_practice_attempts_user_id ON practice_attempts (user_id);
