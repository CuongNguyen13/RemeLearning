package com.remelearning.bff.dto;

import lombok.Data;

import java.time.Instant;

/**
 * One row in the merged grammar history list - either a "học thường" (learn) attempt or a
 * "Thư viện" (library) session, tagged by source. Proxied verbatim from english-service's
 * {@code GrammarHistoryEntryDto}.
 */
@Data
public class GrammarHistoryEntryDto {
	private String source; // "LEARN" or "LIBRARY"
	private Long attemptOrSessionId;
	private Instant completedAt;
	private Double score;
	private Long topicId;
}
