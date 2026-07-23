package com.remelearning.bff.dto;

import lombok.Data;

import java.util.List;

/**
 * A pool question shown on the topic's theory page — answer/explanation ARE included since this is
 * the "read the theory" view, not an in-progress quiz (see {@link GrammarSessionQuestionDto} for the
 * answer-hidden shape used while a session is being answered).
 */
@Data
public class GrammarLibraryQuestionDto {
	private Long questionId;
	private String type;
	private String prompt;
	private List<String> options;
	private String answer;
	private String explanationVi;
	private String translationVi;
}
