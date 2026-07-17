-- Mirrors recommendation-service's recommendations.exercises column: concrete practice exercises
-- for the weak point, carried through recommendation.generated. Stored as a JSON array string;
-- nullable since existing rows predate this feature.
ALTER TABLE recent_recommendations ADD COLUMN exercises TEXT;
