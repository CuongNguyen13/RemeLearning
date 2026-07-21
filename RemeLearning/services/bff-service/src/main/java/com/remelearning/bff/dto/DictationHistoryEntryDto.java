package com.remelearning.bff.dto;

import lombok.Data;

import java.time.Instant;

/** One past graded dictation attempt, proxied from english-service.
 * Includes attemptCount so the UI shows how many times the learner has practiced each lesson. */
@Data
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
	/** LIBRARY (clipId present) or AI_PRACTICE (clipId null), for the UI to badge each row. */
	private String practiceType;
}
