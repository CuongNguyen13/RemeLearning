package com.remelearning.bff.dto;

import lombok.Data;

@Data
public class GrammarAttemptQuestionResultDto {
	private int index;
	private String prompt;
	private String yourAnswer;
	private String correctAnswer;
	private boolean correct;
	private String translation;
	private String translationVi;
}
