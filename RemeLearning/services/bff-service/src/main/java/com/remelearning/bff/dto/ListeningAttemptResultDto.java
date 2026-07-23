package com.remelearning.bff.dto;

import lombok.Data;

import java.util.List;

@Data
public class ListeningAttemptResultDto {
	private double accuracy;
	private List<ListeningAttemptQuestionResultDto> results;
	private String transcript;
	private String translation;
	private List<String> actionAdvice;
}
