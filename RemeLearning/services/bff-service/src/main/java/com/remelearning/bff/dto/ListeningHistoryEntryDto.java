package com.remelearning.bff.dto;

import lombok.Data;

import java.time.Instant;

/**
 * One row in the merged listening history list - either a "học thường" (learn) attempt or a
 * "Thư viện" (library) section attempt, tagged by source. Proxied verbatim from english-service's
 * {@code ListeningHistoryEntryDto}.
 */
@Data
public class ListeningHistoryEntryDto {
	private String source; // "LEARN" or "LIBRARY"
	private Long attemptOrSessionId;
	private Instant completedAt;
	private Double score;
	private Long sectionId;
}
