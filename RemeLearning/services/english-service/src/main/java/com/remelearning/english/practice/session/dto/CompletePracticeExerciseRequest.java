package com.remelearning.english.practice.session.dto;

import lombok.Data;

/** Reports the score (0-100) a learner earned on one exercise slot after submitting it via its domain. */
@Data
public class CompletePracticeExerciseRequest {
	private Double score;
}
