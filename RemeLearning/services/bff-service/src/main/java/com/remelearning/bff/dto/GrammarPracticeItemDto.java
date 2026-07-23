package com.remelearning.bff.dto;

import lombok.Data;

import java.time.Instant;
import java.util.List;

/** One AI-generated grammar practice set, proxied from english-service. */
@Data
public class GrammarPracticeItemDto {
	private Long practiceItemId;
	private String level;
	private String examType;
	private String topic;
	private List<String> targetRules;
	private List<GrammarQuestionDto> questions;
	private Instant createdAt;
}
