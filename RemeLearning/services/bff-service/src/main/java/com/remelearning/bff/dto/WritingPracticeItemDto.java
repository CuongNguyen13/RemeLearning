package com.remelearning.bff.dto;

import lombok.Data;

import java.time.Instant;
import java.util.List;

/**
 * One writing/translation prompt, proxied from english-service. Has no reference-answer field by
 * design - the model answer must not reach the browser before the learner submits.
 */
@Data
public class WritingPracticeItemDto {
	private Long practiceItemId;
	private String taskType;
	private String level;
	private String examType;
	private String topic;
	private String promptText;
	private String sourceLang;
	private String targetLang;
	private List<String> targetLabels;
	private Instant createdAt;
}
