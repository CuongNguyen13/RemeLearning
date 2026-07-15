package com.remelearning.dashboard.service;

import com.remelearning.dashboard.dto.DashboardSummaryResponse;
import com.remelearning.common.event.LearningGapAnalyzedEvent;
import com.remelearning.common.event.RecommendationGeneratedEvent;

/**
 * Builds and reads back a learner's cross-domain dashboard from the two Kafka events
 * dashboard-service consumes. Callers (Kafka consumers, controller) depend on this interface,
 * not {@link com.remelearning.dashboard.service.impl.DashboardServiceImpl}, so the persistence
 * strategy behind it can change later without touching them.
 */
public interface DashboardService {

	/** Upserts every weak point in the event into the unified weak_points_snapshot table. */
	void recordWeakPoint(LearningGapAnalyzedEvent event);

	/** Upserts every recommendation in the event into the recent_recommendations table. */
	void recordRecommendations(RecommendationGeneratedEvent event);

	/** Assembles a learner's aggregate dashboard: per-category progress + recent recommendations. */
	DashboardSummaryResponse getSummary(String userId);
}
