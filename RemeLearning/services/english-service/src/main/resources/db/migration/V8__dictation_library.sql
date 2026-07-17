-- Rebuilds the dictation ("nghe chép chính tả") domain around a fixed library of real recorded
-- audio clips (imported from disk/cloud via common's StorageClient) instead of per-weak-point
-- generated sentences. Attempts are graded (WER) and every miss is recorded so both the immediate
-- Gemini suggestion and the recommendation pipeline (learning.gap.analyzed) can act on it. A second
-- "Luyện nghe với AI" section stores Gemini-suggested practice sentences voiced by Supertonic TTS.

-- Drop the old generated-sentence tables (feature redesigned; dev DB, no data to preserve).
DROP TABLE IF EXISTS dictation_attempts;
DROP TABLE IF EXISTS dictation_exercise_audio;
DROP TABLE IF EXISTS dictation_exercises;

-- The fixed library catalog. One row per recorded clip, tagged with the taxonomy the UI filters by:
-- macro skill (Listening/Speaking/Reading/Writing), CEFR level (A1..C2), topic, and exam/practice
-- type (TOEIC/IELTS/...). script_text is the reference transcript; storage_key locates the audio in
-- whatever StorageClient backs the service (local filesystem by default).
CREATE TABLE dictation_clips (
    id BIGSERIAL PRIMARY KEY,
    code VARCHAR(200) NOT NULL UNIQUE,
    title VARCHAR(300) NOT NULL,
    skill VARCHAR(30) NOT NULL,
    level VARCHAR(10),
    topic VARCHAR(200),
    exam_type VARCHAR(40) NOT NULL,
    script_text TEXT NOT NULL,
    storage_key VARCHAR(500) NOT NULL,
    source VARCHAR(40) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_dictation_clips_facets ON dictation_clips (exam_type, level, skill);

-- Gemini-suggested practice sentences (from a learner's recurring misses) for the "Luyện nghe với
-- AI" section. storage_key is null until Supertonic synthesizes the audio for it.
CREATE TABLE dictation_practice_items (
    id BIGSERIAL PRIMARY KEY,
    user_id VARCHAR(100) NOT NULL,
    sentence_text TEXT NOT NULL,
    source VARCHAR(40) NOT NULL,
    storage_key VARCHAR(500),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_dictation_practice_items_user_id ON dictation_practice_items (user_id);

-- One graded attempt. Exactly one of clip_id / practice_item_id is set: a library-clip attempt or an
-- AI-practice-clip attempt, both graded by the same flow.
CREATE TABLE dictation_attempts (
    id BIGSERIAL PRIMARY KEY,
    clip_id BIGINT REFERENCES dictation_clips (id),
    practice_item_id BIGINT REFERENCES dictation_practice_items (id),
    user_id VARCHAR(100) NOT NULL,
    user_transcript TEXT NOT NULL,
    accuracy DOUBLE PRECISION NOT NULL,
    wer DOUBLE PRECISION NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_dictation_attempts_user_id ON dictation_attempts (user_id);

-- The "hay quên" ledger: one row per mis-transcribed/omitted word in an attempt. Drives the AI
-- analysis and the forgetting-score fed into learning.gap.analyzed.
CREATE TABLE dictation_misses (
    id BIGSERIAL PRIMARY KEY,
    attempt_id BIGINT NOT NULL REFERENCES dictation_attempts (id),
    user_id VARCHAR(100) NOT NULL,
    clip_id BIGINT,
    expected_word VARCHAR(200) NOT NULL,
    actual_word VARCHAR(200),
    tag VARCHAR(20) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_dictation_misses_user_word ON dictation_misses (user_id, expected_word);
