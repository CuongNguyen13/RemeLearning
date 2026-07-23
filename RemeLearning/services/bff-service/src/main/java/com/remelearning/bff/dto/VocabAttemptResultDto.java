package com.remelearning.bff.dto;

import lombok.Data;

import java.util.List;

@Data
public class VocabAttemptResultDto {
	private double accuracy;
	private List<VocabAttemptQuestionResultDto> results;
	private List<String> actionAdvice;
}
