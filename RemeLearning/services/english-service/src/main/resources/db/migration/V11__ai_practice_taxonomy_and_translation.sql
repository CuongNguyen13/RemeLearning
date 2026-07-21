-- Adds the level/examType/topic taxonomy (mirroring dictation_clips) plus a translation column to
-- AI-generated practice passages, and a per-sentence translation column to the library's sentences.
ALTER TABLE dictation_practice_items
    ADD COLUMN level VARCHAR(10),
    ADD COLUMN exam_type VARCHAR(40),
    ADD COLUMN topic VARCHAR(200),
    ADD COLUMN translation_text TEXT;

ALTER TABLE dictation_clip_sentences
    ADD COLUMN translation TEXT;
