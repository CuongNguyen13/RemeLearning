package com.remelearning.english.vocabulary.learn.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/** One AI-generated vocabulary practice set (row in {@code vocab_practice_items}). */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VocabPracticeItem {
	private Long id;
	private String userId;
	private String level;
	private String examType;
	private String topic;
	/** JSON array of the target words this set was generated to drill. */
	private String targetWordsJson;
	/** JSON array of {@link VocabQuestionItem}. */
	private String itemsJson;
	private Instant createdAt;
}
