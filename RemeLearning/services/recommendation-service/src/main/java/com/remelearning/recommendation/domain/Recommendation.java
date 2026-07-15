package com.remelearning.recommendation.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * A recommendation row derived from ai-service's {@code learning.gap.analyzed} event.
 * Unlike english-service's per-domain weak-point tables (which filter to one category each),
 * recommendation-service persists every weak point regardless of category (vocabulary, grammar,
 * pronunciation) as a single "recommendation" to act on.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Recommendation {
	private Long id;
	private String userId;
	private String recordingId;
	private String itemId;
	private String category;
	private String label;
	private double forgettingScore;
	private String recommendationText;
	private Instant updatedAt;
}
