package com.remelearning.bff.dto;

import lombok.Data;

import java.time.Instant;

/** One of a learner's scored speaking-library sentence attempts, proxied from english-service. */
@Data
public class SpeakingLibraryHistoryEntryDto {
	private Long id;
	private Long sectionId;
	private Long sentenceId;
	private Double phonemeScore;
	private Double wordScore;
	private Instant createdAt;
}
