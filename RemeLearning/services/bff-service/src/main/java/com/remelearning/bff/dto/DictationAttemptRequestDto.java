package com.remelearning.bff.dto;

import lombok.Data;

/**
 * Body for POST /api/v1/learners/{userId}/dictation/attempts; proxied straight to english-service.
 * Exactly one of clipId / practiceItemId is set (library clip vs AI-practice clip).
 */
@Data
public class DictationAttemptRequestDto {
	private String userId;
	private Long clipId;
	private Long practiceItemId;
	private String userTranscript;
}
