-- "Học & Luyện tập với AI" - vocabulary skill (english.vocabulary.learn). AI-generated practice
-- sets (cloze/MCQ/matching-by-meaning questions) and the learner's graded attempts against them.
-- Weak-point tracking itself keeps reusing the existing vocabulary_weak_points table/Kafka
-- consumer (via PracticeService.redo, see VocabLearnServiceImpl) - no new weak-point table here.

CREATE TABLE vocab_practice_items (
    id BIGSERIAL PRIMARY KEY,
    user_id VARCHAR(100) NOT NULL,
    level VARCHAR(10),
    exam_type VARCHAR(40),
    topic VARCHAR(200),
    target_words TEXT NOT NULL,
    items TEXT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_vocab_practice_items_user_id ON vocab_practice_items (user_id);

CREATE TABLE vocab_practice_attempts (
    id BIGSERIAL PRIMARY KEY,
    practice_item_id BIGINT NOT NULL REFERENCES vocab_practice_items (id),
    user_id VARCHAR(100) NOT NULL,
    answers TEXT NOT NULL,
    score DOUBLE PRECISION NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_vocab_practice_attempts_user_id ON vocab_practice_attempts (user_id);
CREATE INDEX idx_vocab_practice_attempts_item_id ON vocab_practice_attempts (practice_item_id);
