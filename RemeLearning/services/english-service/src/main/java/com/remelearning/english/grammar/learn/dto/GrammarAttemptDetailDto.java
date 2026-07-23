package com.remelearning.english.grammar.learn.dto;

import lombok.Builder;
import lombok.Getter;

import java.time.Instant;
import java.util.List;

@Getter
@Builder
public class GrammarAttemptDetailDto {
	private Long attemptId;
	private String level;
	private String examType;
	private String topic;
	private double accuracy;
	private List<GrammarAttemptQuestionResultDto> results;
	private Instant attemptedAt;
}
