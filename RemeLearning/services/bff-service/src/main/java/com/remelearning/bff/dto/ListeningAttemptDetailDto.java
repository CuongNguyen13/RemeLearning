package com.remelearning.bff.dto;

import lombok.Data;

import java.time.Instant;
import java.util.List;

@Data
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
