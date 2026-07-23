package com.remelearning.bff.dto;

import lombok.Data;

import java.time.Instant;
import java.util.List;

@Data
public class GrammarAttemptDetailDto {
	private Long attemptId;
	private String level;
	private String examType;
	private String topic;
	private double accuracy;
	private List<GrammarAttemptQuestionResultDto> results;
	private Instant attemptedAt;
}
