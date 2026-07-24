package com.remelearning.english.grammar.history.dto;

import lombok.Builder;
import lombok.Getter;

import java.time.Instant;

/**
 * One row in the merged grammar history list — either a "học thường" (learn) attempt or a
 * "Thư viện" (library) session, normalized to the same shape so the FE can render one
 * time-sorted list tagged by {@link #source}. {@code topicId} is only populated for
 * {@code LIBRARY} rows (the FE's "Làm lại" reopen-topic navigation target); learn rows leave it
 * {@code null} since a learn attempt isn't tied to a library topic.
 */
@Getter
@Builder
public class GrammarHistoryEntryDto {
	private String source; // "LEARN" or "LIBRARY"
	private Long attemptOrSessionId;
	private Instant completedAt;
	private Double score;
	private Long topicId;
}
