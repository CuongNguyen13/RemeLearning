package com.remelearning.english.vocabulary.learn.dto;

import lombok.Builder;
import lombok.Getter;

/** One graded question, revealed after submission (unlike {@link VocabQuestionDto}, includes the answer). */
@Getter
@Builder
public class VocabAttemptQuestionResultDto {
	private int index;
	private String prompt;
	private String yourAnswer;
	private String correctAnswer;
	private boolean correct;
	private String translation;
}
