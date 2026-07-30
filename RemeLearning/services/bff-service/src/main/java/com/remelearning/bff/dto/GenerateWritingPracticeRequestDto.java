package com.remelearning.bff.dto;

import lombok.Data;

import java.util.List;

/** Facets for generating one AI writing/translation task; proxied verbatim to english-service. */
@Data
public class GenerateWritingPracticeRequestDto {
	/** COMPOSE | TRANSLATE_VI_EN | TRANSLATE_EN_VI - kept as a String so bff never mirrors the enum. */
	private String taskType;
	private String level;
	private String examType;
	private List<String> focusItems;
}
