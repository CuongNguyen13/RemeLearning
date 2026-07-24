package com.remelearning.bff.dto;

import lombok.Data;

import java.time.Instant;

/**
 * One row in the merged speaking history list - either a "học thường" (learn) attempt or a
 * "Thư viện" (library) sentence attempt, tagged by source. Proxied verbatim from english-service's
 * {@code SpeakingHistoryEntryDto}.
 */
@Data
public class SpeakingHistoryEntryDto {
	private String source; // "LEARN" or "LIBRARY"
	private Long attemptOrSessionId;
	private Instant completedAt;
	private Double score;
	private Long sectionId;
	private Long topicId;
}
