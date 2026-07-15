-- Initial schema for recommendation-service (reme_recommendation).
-- Every weak point from ai-service's learning.gap.analyzed event (all categories: vocabulary,
-- grammar, pronunciation - no filtering, unlike english-service's per-domain consumers) is
-- persisted here as a "recommendation" row.
CREATE TABLE recommendations (
    id BIGSERIAL PRIMARY KEY,
    user_id VARCHAR(100) NOT NULL,
    recording_id VARCHAR(100) NOT NULL,
    item_id VARCHAR(100) NOT NULL,
    category VARCHAR(30) NOT NULL,
    label VARCHAR(255) NOT NULL,
    forgetting_score DOUBLE PRECISION NOT NULL,
    recommendation_text TEXT NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_recommendations_user_item UNIQUE (user_id, item_id)
);

CREATE INDEX idx_recommendations_user_id ON recommendations(user_id);
CREATE INDEX idx_recommendations_category ON recommendations(category);
