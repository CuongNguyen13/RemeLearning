package com.remelearning.english.writing.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/** One graded attempt at a {@link WritingPracticeItem} (row in {@code writing_attempts}). */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WritingAttempt {
	private Long id;
	private Long practiceItemId;
	private String userId;
	private String submittedText;
	/** The grader's corrected rewrite of {@link #submittedText}. */
	private String correctedText;
	private double overallScore;
	/** JSON object of {@link WritingCriteriaScores}. */
	private String criteriaJson;
	/** JSON array of {@link WritingErrorItem} - persisted because grading is LLM-backed (expensive,
	 * non-deterministic) and must not be re-run just to view history or to target a retry prompt. */
	private String errorsJson;
	private String feedback;
	private Instant createdAt;
}
