package com.remelearning.dashboard.dto;

import com.remelearning.dashboard.domain.RecommendationSnapshot;

import java.util.List;

/** Aggregate response for {@code GET /api/v1/dashboard/{userId}}: per-category progress plus the most recent recommendations. */
public record DashboardSummaryResponse(
		String userId,
		List<CategoryProgress> categoryProgress,
		List<RecommendationSnapshot> recentRecommendations) {
}
