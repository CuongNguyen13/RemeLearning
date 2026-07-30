package com.remelearning.english.practice.session.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * One practice session for a learner: a bundle of {@code totalExercises} AI-generated exercises
 * (mixed across the four skills, aimed at the learner's top weak points). This row only tracks the
 * session's lifecycle/progress; the exercises themselves are {@link PracticeSessionExercise} rows.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PracticeSession {
	private Long id;
	private String userId;
	private PracticeSessionStatus status;
	private int totalExercises;
	private Instant createdAt;
	private Instant completedAt;
}
