package com.remelearning.bff.dto;

import lombok.Data;

import java.util.List;

/** Body for POST /api/v1/learners/{userId}/practice/redo; proxied straight to english-service. */
@Data
public class PracticeRedoRequestDto {
	private String userId;
	private List<PracticeAttemptDto> attempts;
}
