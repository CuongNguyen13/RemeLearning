package com.remelearning.bff.dto;

import lombok.Data;

/** Request body to grade one submitted answer within an in-progress grammar-library session. */
@Data
public class SubmitGrammarLibraryAnswerRequestDto {
	private String questionRef;
	private String submittedAnswer;
}
