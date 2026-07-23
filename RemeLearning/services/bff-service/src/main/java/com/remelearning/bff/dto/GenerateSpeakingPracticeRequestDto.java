package com.remelearning.bff.dto;

import lombok.Data;

import java.util.List;

/** Facets for generating one AI speaking-practice sentence; proxied verbatim to english-service. */
@Data
public class GenerateSpeakingPracticeRequestDto {
	private String level;
	private String examType;
	private List<String> focusItems;
}
