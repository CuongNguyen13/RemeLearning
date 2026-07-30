package com.remelearning.bff.dto;

import lombok.Data;

/** One exercise slot in a practice session (a reference only), proxied from english-service. */
@Data
public class PracticeSessionExerciseDto {
	private int order;
	private String category;
	private Long practiceItemId;
	private String topic;
	private String status;
	private Double score;
}
