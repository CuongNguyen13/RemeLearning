-- Initial schema for recording-service (reme_recording)

CREATE TABLE recordings (
    id BIGSERIAL PRIMARY KEY,
    recording_id VARCHAR(100) NOT NULL UNIQUE,
    user_id VARCHAR(100) NOT NULL,
    s3_bucket VARCHAR(255) NOT NULL,
    s3_key VARCHAR(500) NOT NULL,
    language_code VARCHAR(10) NOT NULL DEFAULT 'en',
    original_filename VARCHAR(255),
    content_type VARCHAR(100),
    status VARCHAR(30) NOT NULL DEFAULT 'UPLOADED',
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_recordings_user_id ON recordings(user_id);
