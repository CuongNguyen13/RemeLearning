package com.remelearning.bff.dto;

import lombok.Data;

import java.time.Instant;

/** One AI-generated speaking-practice sentence, proxied from english-service. */
@Data
public class SpeakingPracticeItemDto {
	private Long practiceItemId;
	private String sampleAudioUrl;
	private String level;
	private String examType;
	private String topic;
	private String targetText;
	private String translation;
	private Instant createdAt;
}
