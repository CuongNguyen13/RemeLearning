-- Concrete practice exercises for the weak point, produced by ExerciseGenerator (rule-based
-- templates by default, or Gemini when recommendation.exercise-generator.mode=llm). Stored as a
-- JSON array string; nullable since existing rows predate this feature.
ALTER TABLE recommendations ADD COLUMN IF NOT EXISTS exercises TEXT;
