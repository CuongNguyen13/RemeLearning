package com.remelearning.english.writing.library.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * One graded attempt at a library prompt (row in {@code writing_library_attempts}). Carries the same
 * grader-output columns as {@code writing_attempts} so both tabs render identically.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WritingLibraryAttempt {
	private Long id;
	private String userId;
	private Long promptId;
	private String submittedText;
	private String correctedText;
	private double score;
	/** JSON object of {@code WritingCriteriaScores}. */
	private String criteriaJson;
	/** JSON array of {@code WritingErrorItem}. */
	private String errorsJson;
	private String feedback;
	private Instant startedAt;
	private Instant completedAt;
}
