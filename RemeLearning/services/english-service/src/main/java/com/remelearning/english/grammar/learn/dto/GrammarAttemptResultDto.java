package com.remelearning.english.grammar.learn.dto;

import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@Builder
public class GrammarAttemptResultDto {
	private double accuracy;
	private List<GrammarAttemptQuestionResultDto> results;
	/** Vietnamese, one line per distinct missed rule. */
	private List<String> actionAdvice;
}
