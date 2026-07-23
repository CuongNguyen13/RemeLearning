package com.remelearning.bff.dto;

import lombok.Data;

@Data
public class ListeningAttemptQuestionResultDto {
	private int index;
	private String prompt;
	private String yourAnswer;
	private String correctAnswer;
	private boolean correct;
	private double subScore;
	private String explanation;
}
