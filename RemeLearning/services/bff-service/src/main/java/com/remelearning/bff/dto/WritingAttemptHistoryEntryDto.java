package com.remelearning.bff.dto;

import lombok.Data;

import java.time.Instant;

/** One row of a learner's writing-practice history. */
@Data
public class WritingAttemptHistoryEntryDto {
	private Long attemptId;
	private Long practiceItemId;
	private String taskType;
	private String level;
	private String examType;
	private String topic;
	private double score;
	private Instant attemptedAt;
}
