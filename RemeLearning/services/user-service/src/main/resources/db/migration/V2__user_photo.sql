-- Profile photo storage (S3-backed), so ai-service can fetch a reference photo per userId
-- for face-recognition enrollment. Both nullable: most users won't have uploaded one yet.
ALTER TABLE users
    ADD COLUMN photo_s3_key VARCHAR(500),
    ADD COLUMN photo_url VARCHAR(1000);
