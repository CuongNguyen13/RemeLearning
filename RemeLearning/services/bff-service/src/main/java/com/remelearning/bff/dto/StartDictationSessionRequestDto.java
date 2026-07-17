package com.remelearning.bff.dto;

import lombok.Data;

/** Body for POST /api/v1/learners/{userId}/dictation/sessions; proxied straight to english-service. */
@Data
public class StartDictationSessionRequestDto {
	private String skill;
	private String level;
	private String topic;
	private String examType;
	private int count = 5;
}
