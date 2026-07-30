-- Writing & Translation practice ("Luyện viết & Luyện dịch"): one domain covering three task types
-- (COMPOSE / TRANSLATE_VI_EN / TRANSLATE_EN_VI), mirroring listening_practice_items' shape (V14).
-- Deliberately NO writing_weak_points table: every error the AI grader reports carries its own
-- category ("grammar" or "vocabulary"), so errors are routed into the existing
-- grammar_weak_points/vocabulary_weak_points rows via PracticeService.redo -> WeakPointDispatcher.
-- That is what lets a "past perfect" mistake made while writing merge with the same label already
-- accumulated from dictation/listening, instead of living in a parallel table.

CREATE TABLE writing_practice_items (
    id BIGSERIAL PRIMARY KEY,
    user_id VARCHAR(100) NOT NULL,
    -- COMPOSE | TRANSLATE_VI_EN | TRANSLATE_EN_VI. Plain VARCHAR, not a PG enum: the enum mapping
    -- and validation live in Java (WritingTaskType), matching listening_topic_progress.status.
    task_type VARCHAR(24) NOT NULL,
    level VARCHAR(16),
    exam_type VARCHAR(32),
    topic VARCHAR(255),
    -- The prompt shown to the learner: a Vietnamese task brief (COMPOSE) or the source passage to
    -- translate (TRANSLATE_*). Always carries its Vietnamese instruction line.
    prompt_text TEXT NOT NULL,
    source_lang VARCHAR(8) NOT NULL,
    target_lang VARCHAR(8) NOT NULL,
    -- Model answer / reference translation. Never returned to the client before submission -
    -- WritingPracticeItemDto has no field for it.
    reference_answer TEXT,
    -- JSON array of the weak-point labels this prompt was generated to target.
    target_labels TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_writing_practice_items_user ON writing_practice_items(user_id);

CREATE TABLE writing_attempts (
    id BIGSERIAL PRIMARY KEY,
    practice_item_id BIGINT NOT NULL REFERENCES writing_practice_items(id),
    user_id VARCHAR(100) NOT NULL,
    submitted_text TEXT NOT NULL,
    -- The grader's corrected rewrite of submitted_text.
    corrected_text TEXT,
    overall_score DOUBLE PRECISION NOT NULL,
    -- JSON object of per-criterion scores (grammar/vocabulary/coherence/accuracy|taskResponse).
    criteria TEXT NOT NULL,
    -- JSON array of labelled errors: {wrong, corrected, label, category, explanationVi, severity}.
    -- This is the column the weak-point/recommendation pipeline is fed from, and what the retry
    -- action ("Luyện lại những lỗi này") re-reads to target a fresh prompt.
    errors TEXT NOT NULL,
    feedback TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_writing_attempts_user ON writing_attempts(user_id);
CREATE INDEX idx_writing_attempts_item ON writing_attempts(practice_item_id);
