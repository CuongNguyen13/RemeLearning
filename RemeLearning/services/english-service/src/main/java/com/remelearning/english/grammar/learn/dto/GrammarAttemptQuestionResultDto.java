package com.remelearning.english.grammar.learn.dto;

import lombok.Builder;
import lombok.Getter;

/** One graded question, revealed after submission (unlike {@link GrammarQuestionDto}, includes the answer). */
@Getter
@Builder
public class GrammarAttemptQuestionResultDto {
	private int index;
	private String prompt;
	private String yourAnswer;
	private String correctAnswer;
	private boolean correct;
	private String translation;
	/** Vietnamese literal translation of {@code correctAnswer}. */
	private String translationVi;
}
