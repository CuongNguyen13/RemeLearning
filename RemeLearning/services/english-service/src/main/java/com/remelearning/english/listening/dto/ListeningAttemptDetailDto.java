package com.remelearning.english.listening.dto;

import lombok.Builder;
import lombok.Getter;

import java.time.Instant;
import java.util.List;

@Getter
@Builder
public class ListeningAttemptDetailDto {
	private Long attemptId;
	private String level;
	private String examType;
	private String topic;
	private double accuracy;
	private List<ListeningAttemptQuestionResultDto> results;
	private String transcript;
	private String translation;
	private Instant attemptedAt;
}
