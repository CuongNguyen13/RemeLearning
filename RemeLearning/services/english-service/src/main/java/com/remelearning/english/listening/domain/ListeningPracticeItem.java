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
	/** Null until Supertonic has synthesized the audio - which happens on first play, not at generation. */
	private String storageKey;
	private String translation;
	/**
	 * JSON array of {@code DialogueLine}, kept so the audio can be synthesized lazily (the flattened
	 * {@link #transcript} can't be split back into speaker-tagged lines). Null for rows generated
	 * before lazy synthesis existed - those already have a {@link #storageKey}.
	 */
	private String linesJson;
	/** JSON array of {@link ListeningQuestionItem}. */
	private String questionsJson;
	private Instant createdAt;
}
