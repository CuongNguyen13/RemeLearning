-- Records which language each transcript segment was transcribed in. Populated by ai-service's
-- per-speaker-turn transcription (each diarized turn is decoded/auto-detected independently, so a
-- recording with speakers using different languages gets one language per segment, not one
-- language for the whole transcript).
ALTER TABLE transcript_segments ADD COLUMN language VARCHAR(10) NOT NULL DEFAULT 'en';
