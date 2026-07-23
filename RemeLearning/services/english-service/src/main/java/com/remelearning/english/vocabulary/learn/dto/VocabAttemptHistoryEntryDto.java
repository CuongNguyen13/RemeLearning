package com.remelearning.english.vocabulary.learn.dto;

import lombok.Builder;
import lombok.Getter;

import java.time.Instant;

@Getter
@Builder
public class VocabAttemptHistoryEntryDto {
	private Long attemptId;
	private Long practiceItemId;
	private String level;
	private String examType;
	private String topic;
	private double score;
	private Instant attemptedAt;
}
