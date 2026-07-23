package com.remelearning.bff.dto;

import lombok.Data;

import java.time.Instant;

/** One of a learner's completed listening-library section attempts, proxied from english-service. */
@Data
public class ListeningLibraryHistoryEntryDto {
	private Long id;
	private Long sectionId;
	private Double score;
	private Integer correctCount;
	private Integer totalQuestions;
	private Instant startedAt;
	private Instant completedAt;
}
