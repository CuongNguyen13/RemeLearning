package com.remelearning.bff.dto;

import lombok.Data;

import java.util.List;

/** Facets for generating one AI listening passage; proxied verbatim to english-service. */
@Data
public class GenerateListeningPracticeRequestDto {
	private String level;
	private String examType;
	private String translationLang;
	private List<String> focusItems;
}
