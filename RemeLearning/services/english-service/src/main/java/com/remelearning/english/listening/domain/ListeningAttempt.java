package com.remelearning.english.listening.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/** One graded attempt at a {@link ListeningPracticeItem} (row in {@code listening_attempts}). */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ListeningAttempt {
	private Long id;
	private Long practiceItemId;
	private String userId;
	/** JSON array of the learner's typed/selected answers, aligned by index to the item's questions. */
	private String answersJson;
	/** JSON array of the computed per-question results (subScore/correct/explanation) - persisted at
	 * submission time rather than recomputed on every read, since OPEN questions are LLM-graded
	 * (expensive, non-deterministic) and shouldn't be re-scored just to view history. */
	private String resultsJson;
	private double score;
	private Instant createdAt;
}
