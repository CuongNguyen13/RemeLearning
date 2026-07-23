package com.remelearning.english.speaking.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/** One AI-generated speaking practice sentence/passage (row in {@code speaking_practice_items}). */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SpeakingPracticeItem {
	private Long id;
	private String userId;
	private String level;
	private String examType;
	private String topic;
	private String targetText;
	/** Null until Supertonic has synthesized the sample (model) audio. */
	private String storageKey;
	private String translation;
	private Instant createdAt;
}
