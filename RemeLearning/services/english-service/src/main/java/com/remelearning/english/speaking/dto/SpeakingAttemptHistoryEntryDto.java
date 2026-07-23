package com.remelearning.english.speaking.dto;

import lombok.Builder;
import lombok.Getter;

import java.time.Instant;

@Getter
@Builder
public class SpeakingAttemptHistoryEntryDto {
	private Long attemptId;
	private Long practiceItemId;
	private String level;
	private String examType;
	private String topic;
	private double overallScore;
	private Instant attemptedAt;
}
