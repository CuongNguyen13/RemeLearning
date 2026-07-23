-- "Học & Luyện tập với AI" - speaking/pronunciation skill (english.speaking, new domain).
-- AI-generated sentences/passages (Gemini + Supertonic sample audio) and the learner's recorded
-- attempts, scored by ai-service's wav2vec2 GOP model (see PronunciationScoringClient). Weak
-- points feed the EXISTING pronunciation_weak_points table/Kafka consumer via PracticeService.redo
-- (see SpeakingLearnServiceImpl) - no new weak-point table here.

CREATE TABLE speaking_practice_items (
    id BIGSERIAL PRIMARY KEY,
    user_id VARCHAR(100) NOT NULL,
    level VARCHAR(10),
    exam_type VARCHAR(40),
    topic VARCHAR(200),
    target_text TEXT NOT NULL,
    storage_key VARCHAR(500),
    translation TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_speaking_practice_items_user_id ON speaking_practice_items (user_id);

CREATE TABLE speaking_attempts (
    id BIGSERIAL PRIMARY KEY,
    practice_item_id BIGINT NOT NULL REFERENCES speaking_practice_items (id),
    user_id VARCHAR(100) NOT NULL,
    audio_storage_key VARCHAR(500) NOT NULL,
    overall_score DOUBLE PRECISION NOT NULL,
    word_scores TEXT NOT NULL,
    transcript TEXT,
    weak_phonemes TEXT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_speaking_attempts_user_id ON speaking_attempts (user_id);
CREATE INDEX idx_speaking_attempts_item_id ON speaking_attempts (practice_item_id);
