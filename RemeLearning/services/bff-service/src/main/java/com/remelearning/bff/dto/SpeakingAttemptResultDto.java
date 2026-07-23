package com.remelearning.bff.dto;

import lombok.Data;

import java.util.List;

@Data
public class SpeakingAttemptResultDto {
	private double overall;
	private List<WordScoreDto> words;
	private String transcript;
	private List<String> weakPhonemes;
	private List<String> actionAdvice;
}
