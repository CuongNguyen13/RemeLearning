-- Listening domain schema for english-service (reme_english). Unlike the other three domain
-- weak-point tables, this one has two distinct sources of the SAME category "listening":
--   1. Dictation (source_type = DICTATION): every word a learner mistypes in a dictation attempt
--      is dual-written here (in addition to its own root-cause category - vocabulary/grammar/
--      pronunciation) via DictationServiceImpl.publishWeakPoints, since a dictation exercise is
--      itself a listening exercise regardless of what root-caused the specific miss.
--   2. Listening comprehension (source_type = COMPREHENSION): scored directly by the practice/redo
--      flow's Java engine (WeakPointDispatcherImpl -> ListeningWeakPointService.applyJavaComputedScore),
--      the same path grammar/vocabulary/pronunciation already use for their own Java-computed scores.
CREATE TABLE listening_weak_points (
    id BIGSERIAL PRIMARY KEY,
    recording_id VARCHAR(100) NOT NULL,
    user_id VARCHAR(100) NOT NULL,
    item_id VARCHAR(100) NOT NULL,
    label VARCHAR(255) NOT NULL,
    source_type VARCHAR(20) NOT NULL,
    forgetting_score DOUBLE PRECISION NOT NULL,
    recommendation TEXT NOT NULL,
    mastery_level DOUBLE PRECISION,
    next_review_at TIMESTAMPTZ,
    score_source VARCHAR(20) NOT NULL DEFAULT 'PYTHON_LEGACY',
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_listening_weak_points_user_item UNIQUE (user_id, item_id),
    CONSTRAINT chk_listening_weak_points_source_type CHECK (source_type IN ('DICTATION', 'COMPREHENSION')),
    CONSTRAINT chk_listening_weak_points_score_source CHECK (score_source IN ('PYTHON_LEGACY', 'JAVA_ENGINE'))
);

CREATE INDEX idx_listening_weak_points_user_id ON listening_weak_points (user_id);
CREATE INDEX idx_listening_weak_points_source_type ON listening_weak_points (source_type);
