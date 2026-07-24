package com.remelearning.english.listening.history.dto;

import lombok.Builder;
import lombok.Getter;

import java.time.Instant;

/**
 * One row in the merged listening history list — either a "học thường" (learn) attempt or a
 * "Thư viện" (library) section attempt, normalized to the same shape so the FE can render one
 * time-sorted list tagged by {@link #source}. {@code sectionId} is only populated for
 * {@code LIBRARY} rows (the FE's "Làm lại" reopen-section navigation target); learn rows leave it
 * {@code null} since a learn attempt isn't tied to a library section.
 */
@Getter
@Builder
public class ListeningHistoryEntryDto {
	private String source; // "LEARN" or "LIBRARY"
	private Long attemptOrSessionId;
	private Instant completedAt;
	private Double score;
	private Long sectionId;
}
