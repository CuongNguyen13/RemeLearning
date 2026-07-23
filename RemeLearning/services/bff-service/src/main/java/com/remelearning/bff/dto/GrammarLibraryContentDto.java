package com.remelearning.bff.dto;

import lombok.Data;

import java.util.List;

/** A grammar-library topic's theory page (explanation + illustration + examples) plus its question pool. */
@Data
public class GrammarLibraryContentDto {
	private Long topicId;
	private String explanationEn;
	private String explanationVi;
	private String illustrationText;
	private List<GrammarLibraryExampleDto> examples;
	private List<GrammarLibraryQuestionDto> questions;
}
