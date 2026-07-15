-- Initial schema for dashboard-service (reme_dashboard): a cross-domain read model built purely
-- from Kafka events (learning.gap.analyzed, recommendation.generated) - no REST calls to other
-- services. Per-category counts/avg forgetting score are computed at read time via GROUP BY,
-- not maintained as a running counter.

-- One row per (user_id, item_id), unified across all 3 categories (vocabulary/grammar/pronunciation)
-- instead of english-service's 3 separate per-domain tables.
CREATE TABLE weak_points_snapshot (
    id BIGSERIAL PRIMARY KEY,
    user_id VARCHAR(100) NOT NULL,
    recording_id VARCHAR(100) NOT NULL,
    item_id VARCHAR(100) NOT NULL,
    category VARCHAR(30) NOT NULL,
    label VARCHAR(255) NOT NULL,
    forgetting_score DOUBLE PRECISION NOT NULL,
    recommendation TEXT NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_weak_points_snapshot_user_item UNIQUE (user_id, item_id)
);

CREATE INDEX idx_weak_points_snapshot_user_id ON weak_points_snapshot(user_id);

-- One row per (user_id, item_id) recommendation, upserted so re-recommending the same item
-- refreshes it in place instead of duplicating.
CREATE TABLE recent_recommendations (
    id BIGSERIAL PRIMARY KEY,
    user_id VARCHAR(100) NOT NULL,
    item_id VARCHAR(100) NOT NULL,
    category VARCHAR(30) NOT NULL,
    label VARCHAR(255) NOT NULL,
    recommendation_text TEXT NOT NULL,
    forgetting_score DOUBLE PRECISION NOT NULL,
    received_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_recent_recommendations_user_item UNIQUE (user_id, item_id)
);

CREATE INDEX idx_recent_recommendations_user_id ON recent_recommendations(user_id);
