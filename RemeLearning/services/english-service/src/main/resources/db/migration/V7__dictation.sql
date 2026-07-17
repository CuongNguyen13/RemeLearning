-- Dictation-practice domain schema for english-service (reme_english).
-- Sentences are generated from a learner's top vocabulary/grammar weak points (see
-- VocabularyWeakPointMapper/GrammarWeakPointMapper.findTopByUserId), synthesized into audio per
-- accent via Google Cloud TTS, and stored in S3/MinIO under the reme.s3.dictation-bucket bucket.

CREATE TABLE dictation_exercises (
    id BIGSERIAL PRIMARY KEY,
    user_id VARCHAR(100) NOT NULL,
    item_id VARCHAR(100) NOT NULL,
    category VARCHAR(30) NOT NULL,
    label VARCHAR(255) NOT NULL,
    sentence_text TEXT NOT NULL,
    forgetting_score DOUBLE PRECISION NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_dictation_exercises_user_id ON dictation_exercises (user_id);

-- One row per (exercise, accent): a single generated sentence can have several accent audio variants.
CREATE TABLE dictation_exercise_audio (
    id BIGSERIAL PRIMARY KEY,
    exercise_id BIGINT NOT NULL REFERENCES dictation_exercises (id),
    accent VARCHAR(10) NOT NULL,
    s3_key VARCHAR(500) NOT NULL,
    CONSTRAINT uq_dictation_exercise_audio_exercise_accent UNIQUE (exercise_id, accent)
);

CREATE INDEX idx_dictation_exercise_audio_exercise_id ON dictation_exercise_audio (exercise_id);

CREATE TABLE dictation_attempts (
    id BIGSERIAL PRIMARY KEY,
    exercise_id BIGINT NOT NULL REFERENCES dictation_exercises (id),
    user_id VARCHAR(100) NOT NULL,
    user_transcript TEXT NOT NULL,
    accuracy DOUBLE PRECISION NOT NULL,
    wer DOUBLE PRECISION NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_dictation_attempts_user_id ON dictation_attempts (user_id);
CREATE INDEX idx_dictation_attempts_exercise_id ON dictation_attempts (exercise_id);
