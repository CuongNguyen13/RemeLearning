package com.remelearning.bff.dto;

import lombok.Data;

import java.util.List;

/** Facets for generating one AI grammar practice set; proxied verbatim to english-service. */
@Data
public class GenerateGrammarPracticeRequestDto {
	private String level;
	private String examType;
	private List<String> focusItems;
}
