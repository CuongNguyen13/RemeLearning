package com.remelearning.bff.dto;

import lombok.Data;

/** Body for completing one practice-session exercise slot; proxied straight to english-service. */
@Data
public class CompletePracticeExerciseRequestDto {
	private Double score;
}
