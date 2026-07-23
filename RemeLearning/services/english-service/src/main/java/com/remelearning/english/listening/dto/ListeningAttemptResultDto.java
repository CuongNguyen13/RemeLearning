package com.remelearning.english.listening.dto;

import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@Builder
public class ListeningAttemptResultDto {
	private double accuracy;
	private List<ListeningAttemptQuestionResultDto> results;
	private String transcript;
	private String translation;
	private List<String> actionAdvice;
}
