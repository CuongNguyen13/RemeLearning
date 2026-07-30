package com.remelearning.english.writing.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/** One row of a learner's writing-practice history (attempt joined to its prompt). */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WritingAttemptHistoryRow {
	private Long attemptId;
	private Long practiceItemId;
	private WritingTaskType taskType;
	private String level;
	private String examType;
	private String topic;
	private double score;
	private Instant createdAt;
}
