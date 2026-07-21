package com.remelearning.bff.dto;

import lombok.Data;

/** One AI-practice item (Supertonic-voiced), proxied from english-service. */
@Data
public class DictationPracticeItemDto {
	private Long practiceItemId;
	private String audioUrl;
	private String level;
	private String examType;
	private String topic;
}
