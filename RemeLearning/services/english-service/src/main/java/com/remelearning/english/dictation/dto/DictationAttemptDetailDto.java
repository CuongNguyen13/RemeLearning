package com.remelearning.english.dictation.dto;

import lombok.Builder;
import lombok.Getter;

import java.time.Instant;
import java.util.List;

/** Full detail for one past dictation attempt, for GET /api/v1/dictation/history/{userId}/{attemptId}. */
@Getter
@Builder
public class DictationAttemptDetailDto {
	private Long attemptId;
	private String title;
	private String skill;
	private String level;
	private String examType;
	private String referenceText;
	private String userTranscript;
	private double accuracy;
	private double wer;
	private List<DictationMistakeDto> mistakes;
	private List<String> aiSuggestions;
	private Instant attemptedAt;
}
