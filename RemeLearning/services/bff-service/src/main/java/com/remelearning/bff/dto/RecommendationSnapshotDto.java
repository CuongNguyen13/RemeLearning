package com.remelearning.bff.dto;

import lombok.Data;

import java.time.Instant;

/** A recent recommendation as snapshotted by dashboard-service (its own RecommendationSnapshot copy). */
@Data
public class RecommendationSnapshotDto {

	private String userId;
	private String itemId;
	private String category;
	private String label;
	private String recommendationText;
	private Double forgettingScore;
	private Instant receivedAt;
}
