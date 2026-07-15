package com.remelearning.common.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** One item inside a published {@link RecommendationGeneratedEvent}. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RecommendationPayload {
	private String itemId;
	private String category;
	private String label;
	private String recommendationText;
	private double forgettingScore;
}
