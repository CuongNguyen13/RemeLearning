-- "Học & Luyện tập với AI" - grammar skill (english.grammar.learn). AI-generated practice sets
-- (error-correction/fill-tense/transform/MCQ questions) and the learner's graded attempts against
-- them. Weak-point tracking itself keeps reusing the existing grammar_weak_points table/Kafka
-- consumer (via PracticeService.redo, see GrammarLearnServiceImpl) - no new weak-point table here.

CREATE TABLE grammar_practice_items (
    id BIGSERIAL PRIMARY KEY,
    user_id VARCHAR(100) NOT NULL,
    level VARCHAR(10),
    exam_type VARCHAR(40),
    topic VARCHAR(200),
    target_rules TEXT NOT NULL,
    items TEXT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_grammar_practice_items_user_id ON grammar_practice_items (user_id);

CREATE TABLE grammar_practice_attempts (
    id BIGSERIAL PRIMARY KEY,
    practice_item_id BIGINT NOT NULL REFERENCES grammar_practice_items (id),
    user_id VARCHAR(100) NOT NULL,
    answers TEXT NOT NULL,
    score DOUBLE PRECISION NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_grammar_practice_attempts_user_id ON grammar_practice_attempts (user_id);
CREATE INDEX idx_grammar_practice_attempts_item_id ON grammar_practice_attempts (practice_item_id);
