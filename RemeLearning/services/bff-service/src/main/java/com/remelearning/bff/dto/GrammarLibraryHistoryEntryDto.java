package com.remelearning.bff.dto;

import lombok.Data;

import java.time.Instant;

/** One of a learner's completed grammar-library sessions for a topic. */
@Data
public class GrammarLibraryHistoryEntryDto {
	private Long sessionId;
	private Long topicId;
	private String sessionType;
	private int correctCount;
	private int totalCount;
	private double accuracy;
	private Instant completedAt;
}
