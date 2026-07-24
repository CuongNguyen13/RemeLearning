-- Per-question answer detail for a listening library attempt, needed to
-- later regenerate AI practice targeting exactly what was missed.
CREATE TABLE listening_library_attempt_answers (
    id BIGSERIAL PRIMARY KEY,
    attempt_id BIGINT NOT NULL REFERENCES listening_library_attempts(id),
    question_id BIGINT NOT NULL REFERENCES listening_library_questions(id),
    selected_option VARCHAR(8) NOT NULL,
    correct_option VARCHAR(8) NOT NULL,
    is_correct BOOLEAN NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_listening_library_attempt_answers_attempt ON listening_library_attempt_answers(attempt_id);
