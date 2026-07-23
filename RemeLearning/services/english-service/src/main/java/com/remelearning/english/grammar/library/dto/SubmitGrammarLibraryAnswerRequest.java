package com.remelearning.english.grammar.library.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class SubmitGrammarLibraryAnswerRequest {
	@NotBlank
	private String questionRef;

	private String submittedAnswer;
}
