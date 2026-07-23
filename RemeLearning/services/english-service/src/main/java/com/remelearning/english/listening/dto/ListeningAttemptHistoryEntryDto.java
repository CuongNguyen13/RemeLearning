package com.remelearning.english.listening.dto;

import lombok.Builder;
import lombok.Getter;

import java.time.Instant;

@Getter
@Builder
public class ListeningAttemptHistoryEntryDto {
	private Long attemptId;
	private Long practiceItemId;
	private String level;
	private String examType;
	private String topic;
	private double score;
	private Instant attemptedAt;
}
