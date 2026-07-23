package com.remelearning.english.grammar.library.dto;

import com.remelearning.english.grammar.library.domain.GrammarSessionType;
import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@Builder
public class StartGrammarSessionResponse {
	private Long sessionId;
	private GrammarSessionType sessionType;
	private List<GrammarSessionQuestionDto> questions;
	private int totalCount;
}
