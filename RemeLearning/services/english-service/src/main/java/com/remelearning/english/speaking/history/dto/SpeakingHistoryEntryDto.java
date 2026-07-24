package com.remelearning.english.speaking.history.dto;

import lombok.Builder;
import lombok.Getter;

import java.time.Instant;

/**
 * One row in the merged speaking history list — either a "học thường" (learn) attempt or a
 * "Thư viện" (library) sentence attempt, normalized to the same shape so the FE can render one
 * time-sorted list tagged by {@link #source}. {@code sectionId} is only populated for
 * {@code LIBRARY} rows (the FE's "Làm lại" reopen-section navigation target); learn rows leave it
 * {@code null} since a learn attempt isn't tied to a library section. Library granularity matches
 * {@code SpeakingLibraryService#getHistory} exactly - one row per scored sentence attempt, not
 * rolled up per section (speaking-library scores per-sentence, unlike listening-library's
 * per-section granularity).
 */
@Getter
@Builder
public class SpeakingHistoryEntryDto {
	private String source; // "LEARN" or "LIBRARY"
	private Long attemptOrSessionId;
	private Instant completedAt;
	private Double score;
	private Long sectionId;
}
