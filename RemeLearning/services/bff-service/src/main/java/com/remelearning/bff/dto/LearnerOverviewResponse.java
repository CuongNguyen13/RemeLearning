package com.remelearning.bff.dto;

import lombok.Builder;
import lombok.Data;

import java.util.List;

/**
 * Composite response for GET /api/v1/learners/{userId}/overview - the single payload the UI's
 * learner-overview screen needs, fanned out from dashboard-service (progress + recommendations),
 * recording-service (recent recordings), and user-service (profile) so the frontend doesn't have
 * to call all three itself.
 */
@Data
@Builder
public class LearnerOverviewResponse {

	private String userId;
	private List<CategoryProgressDto> categoryProgress;
	private List<RecommendationSnapshotDto> recentRecommendations;
	private List<RecordingDto> recentRecordings;
	private UserDto user;
}
