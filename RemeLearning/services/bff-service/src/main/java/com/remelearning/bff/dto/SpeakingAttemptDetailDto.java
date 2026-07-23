package com.remelearning.bff.dto;

import lombok.Data;

import java.time.Instant;
import java.util.List;

@Data
public class SpeakingAttemptDetailDto {
	private Long attemptId;
	private String level;
	private String examType;
	private String topic;
	private String targetText;
	private double overallScore;
	private List<WordScoreDto> words;
	private String transcript;
	private List<String> weakPhonemes;
	private Instant attemptedAt;
}
