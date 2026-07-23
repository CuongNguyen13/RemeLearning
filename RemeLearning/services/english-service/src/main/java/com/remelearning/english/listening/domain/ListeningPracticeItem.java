package com.remelearning.english.listening.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/** One AI-generated listening-practice passage (row in {@code listening_practice_items}). */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ListeningPracticeItem {
	private Long id;
	private String userId;
	private String level;
	private String examType;
	private String topic;
	private String transcript;
	/** Null until Supertonic has synthesized the audio. */
	private String storageKey;
	private String translation;
	/** JSON array of {@link ListeningQuestionItem}. */
	private String questionsJson;
	private Instant createdAt;
}
