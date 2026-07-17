package com.remelearning.bff.dto;

import lombok.Data;

import java.time.Instant;

/** One past graded dictation attempt, proxied from english-service. */
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
}
