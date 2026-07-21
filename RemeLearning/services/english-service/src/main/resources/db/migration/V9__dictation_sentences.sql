-- Sentence-level dictation (rev 2 of the spec): each clip's script is split into ordered sentences
-- so the FE can play/type one sentence at a time instead of the whole clip in one Textarea. Sentences
-- are seeded by the importer from the plain-text script; start_ms/end_ms are filled in later by a
-- separate AI-alignment step (ai-service STT), so they start out NULL.
CREATE TABLE dictation_clip_sentences (
    id BIGSERIAL PRIMARY KEY,
    clip_id BIGINT NOT NULL REFERENCES dictation_clips(id) ON DELETE CASCADE,
    seq INT NOT NULL,
    text TEXT NOT NULL,
    start_ms INT,
    end_ms INT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (clip_id, seq)
);

-- Folder → file browsing (rev 2): the direct parent directory of the clip's audio file in storage,
-- distinct from the existing skill/level/topic/examType taxonomy which is derived from filename/depth
-- conventions rather than the raw folder structure.
ALTER TABLE dictation_clips ADD COLUMN folder VARCHAR(255) NOT NULL DEFAULT 'general';
CREATE INDEX idx_dictation_clips_folder ON dictation_clips (folder);
