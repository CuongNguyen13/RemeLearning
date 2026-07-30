package com.remelearning.english.writing.dto;

import com.remelearning.english.writing.domain.WritingTaskType;
import lombok.Builder;
import lombok.Data;

import java.time.Instant;

/** One row in the learner's writing-practice history list. */
@Data
@Builder
public class WritingAttemptHistoryEntryDto {
	private Long attemptId;
	private Long practiceItemId;
	private WritingTaskType taskType;
	private String level;
	private String examType;
	private String topic;
	private double score;
	private Instant attemptedAt;
}
