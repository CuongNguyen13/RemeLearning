package com.remelearning.english.vocabulary.learn.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/** One history-list row: an attempt joined with its practice item's taxonomy. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VocabAttemptHistoryRow {
	private Long attemptId;
	private Long practiceItemId;
	private String level;
	private String examType;
	private String topic;
	private double score;
	private Instant createdAt;
}
