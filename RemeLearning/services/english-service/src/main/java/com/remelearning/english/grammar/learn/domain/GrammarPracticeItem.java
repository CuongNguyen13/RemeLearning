package com.remelearning.english.grammar.learn.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/** One AI-generated grammar practice set (row in {@code grammar_practice_items}). */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GrammarPracticeItem {
	private Long id;
	private String userId;
	private String level;
	private String examType;
	private String topic;
	/** JSON array of the target grammar rules this set was generated to drill. */
	private String targetRulesJson;
	/** JSON array of {@link GrammarQuestionItem}. */
	private String itemsJson;
	private Instant createdAt;
}
