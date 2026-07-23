package com.remelearning.english.grammar.library.dto;

import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@Builder
public class GrammarLibraryContentDto {
	private Long topicId;
	private String explanationEn;
	private String explanationVi;
	private String illustrationText;
	private List<GrammarLibraryExampleDto> examples;
	private List<GrammarLibraryQuestionDto> questions;
}
