package com.remelearning.english.vocabulary.learn.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/** One graded attempt at a {@link VocabPracticeItem} (row in {@code vocab_practice_attempts}). */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VocabPracticeAttempt {
	private Long id;
	private Long practiceItemId;
	private String userId;
	/** JSON array of the learner's typed/selected answers, aligned by index to the item's questions. */
	private String answersJson;
	private double score;
	private Instant createdAt;
}
