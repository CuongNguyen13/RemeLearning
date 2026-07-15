package com.remelearning.bff.dto;

import lombok.Data;

import java.util.List;

/** Mirrors dashboard-service's DashboardSummaryResponse: category progress + recent recommendations for one learner. */
@Data
public class DashboardSummaryDto {

	private String userId;
	private List<CategoryProgressDto> categoryProgress;
	private List<RecommendationSnapshotDto> recentRecommendations;
}
