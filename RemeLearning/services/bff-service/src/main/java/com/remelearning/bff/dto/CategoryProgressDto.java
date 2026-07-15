package com.remelearning.bff.dto;

import lombok.Data;

import java.time.Instant;

/** Per-category (vocabulary/grammar/pronunciation) learning progress snapshot, one row of dashboard-service's CategoryProgress. */
@Data
public class CategoryProgressDto {

	private String category;
	private Long weakPointCount;
	private Double avgForgettingScore;
	private Instant lastUpdated;
}
