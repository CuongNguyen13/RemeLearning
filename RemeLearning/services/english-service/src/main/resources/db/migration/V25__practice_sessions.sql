-- Practice-session schema for english-service (reme_english). A "practice session" is the refactored
-- Luyện tập feature: one session bundles ~4 real AI exercises, each a randomly-topiced set for one of
-- the four skills (vocabulary/grammar/listening/speaking), aimed at the learner's highest-scoring weak
-- points. This layer only ORCHESTRATES exercise generation + tracks progress - the actual exercise
-- rows live in each domain's own *_practice_items bank, referenced here by practice_item_id.
CREATE TABLE practice_sessions (
    id BIGSERIAL PRIMARY KEY,
    user_id VARCHAR(100) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'IN_PROGRESS',
    total_exercises INT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    completed_at TIMESTAMPTZ,
    CONSTRAINT chk_practice_sessions_status CHECK (status IN ('IN_PROGRESS', 'COMPLETED'))
);

-- One slot per exercise in a session. practice_item_id points at the domain practice-item bank of the
-- slot's category, but has NO physical FK: each category (vocabulary/grammar/listening/speaking) stores
-- its items in a different table, so a single FK target is impossible - the same multi-table reference
-- pattern the rest of the repo uses.
CREATE TABLE practice_session_exercises (
    id BIGSERIAL PRIMARY KEY,
    session_id BIGINT NOT NULL REFERENCES practice_sessions (id) ON DELETE CASCADE,
    exercise_order INT NOT NULL,
    category VARCHAR(20) NOT NULL,
    practice_item_id BIGINT NOT NULL,
    topic VARCHAR(255),
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    score DOUBLE PRECISION,
    completed_at TIMESTAMPTZ,
    CONSTRAINT uq_practice_session_exercises_order UNIQUE (session_id, exercise_order),
    CONSTRAINT chk_practice_session_exercises_category CHECK (category IN ('vocabulary', 'grammar', 'listening', 'speaking')),
    CONSTRAINT chk_practice_session_exercises_status CHECK (status IN ('PENDING', 'DONE'))
);

-- Look up a learner's in-progress session (for resume) quickly.
CREATE INDEX idx_practice_sessions_user_status ON practice_sessions (user_id, status);
CREATE INDEX idx_practice_session_exercises_session ON practice_session_exercises (session_id);
