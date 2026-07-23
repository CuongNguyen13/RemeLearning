package com.remelearning.english.grammar.library.dto;

import com.remelearning.english.grammar.learn.domain.GrammarQuestionType;
import lombok.Builder;
import lombok.Getter;

import java.util.List;

/**
 * A pool question shown on the topic's theory page — answer/explanation ARE included here since
 * this is the "read the theory" view, not an in-progress quiz (see {@link GrammarSessionQuestionDto}
 * for the answer-hidden shape used while a session is being answered).
 */
@Getter
@Builder
public class GrammarLibraryQuestionDto {
	private Long questionId;
	private GrammarQuestionType type;
	private String prompt;
	private List<String> options;
	private String answer;
	private String explanationVi;
	private String translationVi;
}
