package com.remelearning.bff.dto;

import lombok.Data;

import java.util.List;

/** One hint for the next sentence: a Vietnamese idea plus English scaffolding, never a full sentence. */
@Data
public class WritingSuggestionDto {
	private String ideaVi;
	private String structureHint;
	private List<String> usefulPhrases;
}
