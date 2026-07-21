-- Profile photo storage (S3-backed), so ai-service can fetch a reference photo per userId
-- for face-recognition enrollment. Both nullable: most users won't have uploaded one yet.
ALTER TABLE users
    ADD COLUMN IF NOT EXISTS photo_s3_key VARCHAR(500),
    ADD COLUMN IF NOT EXISTS photo_url VARCHAR(1000);
