package com.remelearning.bff.dto;

import lombok.Data;

import java.time.Instant;

@Data
public class SpeakingAttemptHistoryEntryDto {
	private Long attemptId;
	private Long practiceItemId;
	private String level;
	private String examType;
	private String topic;
	private double overallScore;
	private Instant attemptedAt;
}
