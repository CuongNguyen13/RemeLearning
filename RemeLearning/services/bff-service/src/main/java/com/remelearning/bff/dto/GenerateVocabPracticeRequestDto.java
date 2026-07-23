package com.remelearning.bff.dto;

import lombok.Data;

import java.util.List;

/** Facets for generating one AI vocabulary practice set; proxied verbatim to english-service. */
@Data
public class GenerateVocabPracticeRequestDto {
	private String level;
	private String examType;
	private List<String> focusItems;
}
