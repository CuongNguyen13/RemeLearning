package com.remelearning.english.practice.session.dto;

import lombok.Builder;
import lombok.Getter;

/**
 * One exercise slot as returned to the client - a reference only (category + practiceItemId + topic),
 * not the exercise content. The client fetches the full questions via the domain's existing getItem
 * endpoint and renders them with that domain's existing Runner.
 */
@Getter
@Builder
public class PracticeSessionExerciseDto {
	private int order;
	private String category;
	private Long practiceItemId;
	private String topic;
	private String status;
	private Double score;
}
