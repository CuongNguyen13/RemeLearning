package com.remelearning.bff.dto;

import lombok.Data;

/** Grading result for one submitted grammar-library session answer. */
@Data
public class GrammarLibraryAnswerResultDto {
	private String questionRef;
	private boolean correct;
	private String correctAnswer;
	private String explanationVi;
	private String translationVi;
}
