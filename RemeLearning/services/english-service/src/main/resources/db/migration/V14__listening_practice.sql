-- "Học & Luyện tập với AI" - listening skill (english.listening, new domain). One AI-generated
-- listening passage (Gemini transcript + MCQ/keyword/open questions, Supertonic audio) per row,
-- plus the learner's graded attempts. Weak points for category "listening" flow through the
-- existing learning.gap.analyzed pipeline (see ListeningLearnServiceImpl -> PracticeService.redo)
-- but there is deliberately no listening_weak_points table: unlike vocabulary/grammar/pronunciation,
-- no english-service Kafka consumer persists a per-domain "listening" weak-point row today, so
-- target-word/keyword selection for regeneration is done from this service's own attempt history
-- instead (see findItemsByUserId), the same way dictation queries its own miss table.

CREATE TABLE listening_practice_items (
    id BIGSERIAL PRIMARY KEY,
    user_id VARCHAR(100) NOT NULL,
    level VARCHAR(10),
    exam_type VARCHAR(40),
    topic VARCHAR(200),
    transcript TEXT NOT NULL,
    storage_key VARCHAR(500),
    translation TEXT,
    questions TEXT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_listening_practice_items_user_id ON listening_practice_items (user_id);

CREATE TABLE listening_attempts (
    id BIGSERIAL PRIMARY KEY,
    practice_item_id BIGINT NOT NULL REFERENCES listening_practice_items (id),
    user_id VARCHAR(100) NOT NULL,
    answers TEXT NOT NULL,
    results TEXT NOT NULL,
    score DOUBLE PRECISION NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_listening_attempts_user_id ON listening_attempts (user_id);
CREATE INDEX idx_listening_attempts_item_id ON listening_attempts (practice_item_id);
