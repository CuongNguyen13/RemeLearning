package com.remelearning.english.vocabulary.learn.dto;

import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@Builder
public class VocabAttemptResultDto {
	private double accuracy;
	private List<VocabAttemptQuestionResultDto> results;
	/** Vietnamese, one line per distinct missed word. */
	private List<String> actionAdvice;
}
