package com.remelearning.bff.dto;

import lombok.Data;

import java.time.Instant;
import java.util.List;

/** A recent recommendation as snapshotted by dashboard-service (its own RecommendationSnapshot copy). */
@Data
public class RecommendationSnapshotDto {

	private String userId;
	private String itemId;
	private String category;
	private String label;
	private String recommendationText;
	private List<String> exercises;
	private Double forgettingScore;
	private Instant receivedAt;
}
