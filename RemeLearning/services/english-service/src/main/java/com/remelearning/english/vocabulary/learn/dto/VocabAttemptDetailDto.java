package com.remelearning.english.vocabulary.learn.dto;

import lombok.Builder;
import lombok.Getter;

import java.time.Instant;
import java.util.List;

@Getter
@Builder
public class VocabAttemptDetailDto {
	private Long attemptId;
	private String level;
	private String examType;
	private String topic;
	private double accuracy;
	private List<VocabAttemptQuestionResultDto> results;
	private Instant attemptedAt;
}
