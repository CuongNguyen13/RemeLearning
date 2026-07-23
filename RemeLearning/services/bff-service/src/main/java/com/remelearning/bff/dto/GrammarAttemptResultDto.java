package com.remelearning.bff.dto;

import lombok.Data;

import java.util.List;

@Data
public class GrammarAttemptResultDto {
	private double accuracy;
	private List<GrammarAttemptQuestionResultDto> results;
	private List<String> actionAdvice;
}
