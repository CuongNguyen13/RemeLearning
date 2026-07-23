package com.remelearning.english.grammar.learn.dto;

import lombok.Builder;
import lombok.Getter;

import java.time.Instant;
import java.util.List;

@Getter
@Builder
public class GrammarPracticeItemDto {
	private Long practiceItemId;
	private String level;
	private String examType;
	private String topic;
	private List<String> targetRules;
	private List<GrammarQuestionDto> questions;
	private Instant createdAt;
}
