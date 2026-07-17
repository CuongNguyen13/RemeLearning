package com.remelearning.english.dictation.dto;

import lombok.Builder;
import lombok.Getter;

import java.time.Instant;

/** One past graded attempt, for GET /api/v1/dictation/history/{userId}. */
@Getter
@Builder
public class DictationHistoryEntryDto {
	private Long attemptId;
	private Long clipId;
	private String title;
	private String skill;
	private String level;
	private String examType;
	private double accuracy;
	private double wer;
	private Instant attemptedAt;
}
