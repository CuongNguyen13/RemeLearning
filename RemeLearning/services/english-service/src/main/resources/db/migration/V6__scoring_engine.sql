-- Per-learner scoring state driving the new common.scoring engine (adaptive-Ebbinghaus half-life,
-- Bayesian Knowledge Tracing mastery, Leitner spaced-repetition scheduling). mistake_history has a
-- single writer (this service, via the practice/redo flow) so no cross-writer guard is needed here
-- - that guard only applies to the three domain weak-point tables below.
ALTER TABLE mistake_history
    ADD COLUMN ease_factor     DOUBLE PRECISION NOT NULL DEFAULT 2.5,
    ADD COLUMN half_life_days  DOUBLE PRECISION NOT NULL DEFAULT 7.0,
    ADD COLUMN mastery         DOUBLE PRECISION NOT NULL DEFAULT 0.3,
    ADD COLUMN leitner_box     SMALLINT         NOT NULL DEFAULT 1,
    ADD COLUMN next_review_at  TIMESTAMPTZ,
    ADD COLUMN last_weak_score DOUBLE PRECISION,
    ADD COLUMN label_key       VARCHAR(255);

ALTER TABLE mistake_history
    ADD CONSTRAINT chk_mistake_history_mastery CHECK (mastery BETWEEN 0 AND 1),
    ADD CONSTRAINT chk_mistake_history_ease CHECK (ease_factor >= 1.3),
    ADD CONSTRAINT chk_mistake_history_half_life CHECK (half_life_days > 0),
    ADD CONSTRAINT chk_mistake_history_box CHECK (leitner_box BETWEEN 1 AND 5);

CREATE INDEX idx_mistake_history_next_review_at ON mistake_history (user_id, next_review_at);

-- Population-level (not per-user) Rasch-lite item difficulty aggregate, feeding
-- RaschDifficultyEstimator. Keyed by (category, label_key) rather than item_id: nothing in this
-- system generates item_id as a canonical, cross-user-shared identifier for "the same mistake
-- type" - it's always just passed through from wherever it was first created. label_key (a
-- trimmed/whitespace-collapsed/lowercased normalization of the label, computed once in Java) is
-- the pragmatic proxy already implicit in how the rule-based classifiers pattern-match on label
-- content. Known limitation: two differently-worded labels for what a human would call the same
-- mistake fragment into separate buckets here; fixing that needs a real canonical-item-identity
-- system, which is out of scope for this change.
CREATE TABLE item_difficulty_stats (
    category        VARCHAR(30)  NOT NULL,
    label_key       VARCHAR(255) NOT NULL,
    correct_count   BIGINT NOT NULL DEFAULT 0,
    incorrect_count BIGINT NOT NULL DEFAULT 0,
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (category, label_key)
);

-- The three domain weak-point tables gain a second writer (the practice/redo flow, in addition to
-- the existing learning.gap.analyzed Kafka consumer) once the new engine is wired in.
-- score_source lets the upsert (see each mapper's XML) refuse to let a stale Python-sourced
-- recompute silently clobber a fresher Java-computed score for the same row.
ALTER TABLE vocabulary_weak_points
    ADD COLUMN mastery_level  DOUBLE PRECISION,
    ADD COLUMN next_review_at TIMESTAMPTZ,
    ADD COLUMN score_source   VARCHAR(20) NOT NULL DEFAULT 'PYTHON_LEGACY';
ALTER TABLE vocabulary_weak_points
    ADD CONSTRAINT chk_vocabulary_weak_points_source CHECK (score_source IN ('PYTHON_LEGACY', 'JAVA_ENGINE'));

ALTER TABLE grammar_weak_points
    ADD COLUMN mastery_level  DOUBLE PRECISION,
    ADD COLUMN next_review_at TIMESTAMPTZ,
    ADD COLUMN score_source   VARCHAR(20) NOT NULL DEFAULT 'PYTHON_LEGACY';
ALTER TABLE grammar_weak_points
    ADD CONSTRAINT chk_grammar_weak_points_source CHECK (score_source IN ('PYTHON_LEGACY', 'JAVA_ENGINE'));

ALTER TABLE pronunciation_weak_points
    ADD COLUMN mastery_level  DOUBLE PRECISION,
    ADD COLUMN next_review_at TIMESTAMPTZ,
    ADD COLUMN score_source   VARCHAR(20) NOT NULL DEFAULT 'PYTHON_LEGACY';
ALTER TABLE pronunciation_weak_points
    ADD CONSTRAINT chk_pronunciation_weak_points_source CHECK (score_source IN ('PYTHON_LEGACY', 'JAVA_ENGINE'));
