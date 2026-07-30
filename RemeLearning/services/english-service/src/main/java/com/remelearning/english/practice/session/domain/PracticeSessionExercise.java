package com.remelearning.english.practice.session.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * One exercise slot in a practice session. {@code category} names the skill (vocabulary/grammar/
 * listening/speaking) and {@code practiceItemId} points at that domain's own practice-item bank -
 * there is deliberately no physical FK since each category lives in a different table. The learner
 * runs the exercise through the existing per-domain Runner/submit endpoint, then reports the score
 * back here via completeExercise.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PracticeSessionExercise {
	private Long id;
	private Long sessionId;
	private int exerciseOrder;
	private String category;
	private Long practiceItemId;
	private String topic;
	private PracticeExerciseStatus status;
	private Double score;
	private Instant completedAt;
}
