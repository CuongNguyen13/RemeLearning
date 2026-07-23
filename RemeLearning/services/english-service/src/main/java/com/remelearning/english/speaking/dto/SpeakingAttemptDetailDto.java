package com.remelearning.english.speaking.dto;

import lombok.Builder;
import lombok.Getter;

import java.time.Instant;
import java.util.List;

@Getter
@Builder
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
