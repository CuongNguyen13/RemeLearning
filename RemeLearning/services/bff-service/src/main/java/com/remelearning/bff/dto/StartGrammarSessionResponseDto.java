package com.remelearning.bff.dto;

import lombok.Data;

import java.util.List;

/** Result of starting (or retrying) a grammar-library session: its questions with answers hidden. */
@Data
public class StartGrammarSessionResponseDto {
	private Long sessionId;
	private String sessionType;
	private List<GrammarSessionQuestionDto> questions;
	private int totalCount;
}
