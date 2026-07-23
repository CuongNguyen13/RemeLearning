package com.remelearning.bff.dto;

import lombok.Data;

import java.time.Instant;
import java.util.List;

/** One AI-generated vocabulary practice set, proxied from english-service. */
@Data
public class VocabPracticeItemDto {
	private Long practiceItemId;
	private String level;
	private String examType;
	private String topic;
	private List<String> targetWords;
	private List<VocabQuestionDto> questions;
	private Instant createdAt;
}
