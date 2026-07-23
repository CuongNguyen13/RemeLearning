package com.remelearning.english.grammar.library.dto;

import com.remelearning.english.grammar.learn.domain.GrammarQuestionType;
import lombok.Builder;
import lombok.Getter;

import java.util.List;

/** One in-progress session question — answer/explanation deliberately omitted, unlike {@link GrammarLibraryQuestionDto}. */
@Getter
@Builder
public class GrammarSessionQuestionDto {
	private String questionRef;
	private GrammarQuestionType type;
	private String prompt;
	private List<String> options;
}
