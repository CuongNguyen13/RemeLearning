package com.remelearning.english.listening.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ListeningAttemptHistoryRow {
	private Long attemptId;
	private Long practiceItemId;
	private String level;
	private String examType;
	private String topic;
	private double score;
	private Instant createdAt;
}
