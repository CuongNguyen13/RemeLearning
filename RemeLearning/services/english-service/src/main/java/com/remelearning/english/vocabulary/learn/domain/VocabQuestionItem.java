package com.remelearning.english.vocabulary.learn.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

/**
 * One AI-generated exercise question targeting a single vocabulary word - the unit stored (as a
 * JSON array) inside {@link VocabPracticeItem#getItemsJson()}. Setters + no-arg constructor exist
 * alongside the builder so it round-trips through Jackson.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VocabQuestionItem {
	private String targetWord;
	private VocabQuestionType type;
	private String prompt;
	/** Null for {@code CLOZE}; the answer/distractor choices for {@code MCQ}/{@code MATCHING}. */
	private List<String> options;
	private String answer;
	private String translation;
}
