package com.remelearning.english.speaking.dto;

import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@Builder
public class SpeakingAttemptResultDto {
	private double overall;
	private List<WordScoreDto> words;
	/** What the learner actually said, per ai-service's own ASR pass - for comparison against the target text. */
	private String transcript;
	private List<String> weakPhonemes;
	private List<String> actionAdvice;
}
