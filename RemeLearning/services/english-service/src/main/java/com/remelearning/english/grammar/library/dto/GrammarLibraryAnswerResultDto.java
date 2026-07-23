package com.remelearning.english.grammar.library.dto;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class GrammarLibraryAnswerResultDto {
	private String questionRef;
	private boolean correct;
	private String correctAnswer;
	private String explanationVi;
	private String translationVi;
}
