package com.remelearning.english.dictation.dto;

import lombok.Builder;
import lombok.Getter;

import java.time.Instant;

/** One past graded attempt, for GET /api/v1/dictation/history/{userId}.
 * Includes attemptCount so the UI can show how many times the learner has practiced each lesson. */
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
	/** How many attempts (including this one) the learner has made on this clip; null for AI-practice entries. */
	private Integer attemptCount;
	/** LIBRARY (clipId present) or AI_PRACTICE (clipId null), so the UI can badge each row. */
	private DictationPracticeType practiceType;
}
