package com.remelearning.recommendation.service;

import com.remelearning.recommendation.domain.Recommendation;
import com.remelearning.common.event.LearningGapAnalyzedEvent;

import java.util.List;
import java.util.Map;

/**
 * Persists every weak point carried by a {@code learning.gap.analyzed} event as a recommendation
 * (no category filtering, unlike english-service's per-domain services) and exposes read access
 * for the REST layer.
 */
public interface RecommendationService {

	/** Upserts one recommendation row per weak point in the event, then publishes recommendation.generated. */
	void handleLearningGapAnalyzed(LearningGapAnalyzedEvent event);

	/** Lists a learner's recommendations, optionally filtered by category, sorted by forgetting score desc. */
	List<Recommendation> getByUserId(String userId, String category);

	/** Same as {@link #getByUserId}, grouped by category. */
	Map<String, List<Recommendation>> getByUserIdGrouped(String userId);
}
