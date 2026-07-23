package com.remelearning.bff.dto;

import lombok.Data;

import java.util.List;

/** One in-progress session question — answer/explanation deliberately omitted, unlike {@link GrammarLibraryQuestionDto}. */
@Data
public class GrammarSessionQuestionDto {
	private String questionRef;
	private String type;
	private String prompt;
	private List<String> options;
}
