-- Per-attempt weak-phoneme detail for Speaking Library, mirroring speaking_attempts.weak_phonemes_json
-- (see V?__pronunciation.sql / speaking_attempts) - needed for a later "AI retry targeting past
-- mistakes" feature that reads which phonemes a learner struggled with, not just the aggregate scores.
ALTER TABLE speaking_library_attempts ADD COLUMN weak_phonemes_json TEXT;
