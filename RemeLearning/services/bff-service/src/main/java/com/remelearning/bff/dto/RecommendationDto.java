package com.remelearning.bff.dto;

import lombok.Data;

import java.time.Instant;
import java.util.List;

/** A learning recommendation for one weak point, as returned by recommendation-service's Recommendation. */
@Data
public class RecommendationDto {

	private String itemId;
	private String category;
	private String label;
	private Double forgettingScore;
	private String recommendationText;
	private List<String> exercises;
	private Instant updatedAt;
}
