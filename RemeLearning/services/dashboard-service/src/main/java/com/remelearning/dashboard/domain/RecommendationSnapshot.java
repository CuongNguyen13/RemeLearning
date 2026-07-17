package com.remelearning.dashboard.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;

/**
 * A single personalized study recommendation for a learner, derived from
 * recommendation-service's {@code recommendation.generated} event.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RecommendationSnapshot {
	private Long id;
	private String userId;
	private String itemId;
	private String category;
	private String label;
	private String recommendationText;
	private List<String> exercises;
	private double forgettingScore;
	private Instant receivedAt;
}
