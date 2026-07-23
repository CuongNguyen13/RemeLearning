package com.remelearning.bff.dto;

import lombok.Data;

import java.time.Instant;
import java.util.List;

/** One AI-generated listening passage, proxied from english-service (no transcript before grading). */
@Data
public class ListeningPracticeItemDto {
	private Long practiceItemId;
	private String audioUrl;
	private String level;
	private String examType;
	private String topic;
	private List<ListeningQuestionDto> questions;
	private Instant createdAt;
}
