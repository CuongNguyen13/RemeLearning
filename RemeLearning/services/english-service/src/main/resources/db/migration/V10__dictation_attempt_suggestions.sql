-- Persists the AI suggestions generated at submit time (previously only returned in the HTTP
-- response, never saved) so a learner can replay them later from History detail.
ALTER TABLE dictation_attempts ADD COLUMN ai_suggestions TEXT;
