package com.remelearning.bff.dto;

import lombok.Data;

/** Facets for generating one AI-practice passage; proxied verbatim to english-service. */
@Data
public class GenerateAiPracticeRequestDto {
	private String level;
	private String examType;
	private String translationLang;
}
