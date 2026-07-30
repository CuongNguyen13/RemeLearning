package com.remelearning.bff.dto;

import lombok.Data;

/** Body for POST /api/v1/learners/{userId}/practice/sessions; userId is stamped from the path. */
@Data
public class StartPracticeSessionRequestDto {
	private String userId;
	private Integer exerciseCount;
}
