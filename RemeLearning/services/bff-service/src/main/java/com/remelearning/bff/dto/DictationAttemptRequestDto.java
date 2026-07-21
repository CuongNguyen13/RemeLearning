package com.remelearning.bff.dto;

import lombok.Data;

import java.util.List;

/**
 * Body for POST /api/v1/learners/{userId}/dictation/attempts; proxied straight to english-service.
 * Exactly one of clipId / practiceItemId is set (library clip vs AI-practice clip). sentenceMistakes
 * is only present for sentence-mode library attempts, null/empty otherwise.
 */
@Data
public class DictationAttemptRequestDto {
	private String userId;
	private Long clipId;
	private Long practiceItemId;
	private String userTranscript;
	private List<DictationSentenceMistakeDto> sentenceMistakes;
}
