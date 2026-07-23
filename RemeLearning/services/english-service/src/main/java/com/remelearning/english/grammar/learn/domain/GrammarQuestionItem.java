package com.remelearning.english.grammar.learn.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

/**
 * One AI-generated exercise question targeting a single grammar rule - the unit stored (as a JSON
 * array) inside {@link GrammarPracticeItem#getItemsJson()}. Setters + no-arg constructor exist
 * alongside the builder so it round-trips through Jackson.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GrammarQuestionItem {
	private String targetRule;
	private GrammarQuestionType type;
	private String prompt;
	/** Null for {@code ERROR_CORRECTION}/{@code FILL_TENSE}/{@code TRANSFORM}; the choices for {@code MCQ}. */
	private List<String> options;
	private String answer;
	/** Vietnamese, one-line explanation of the rule the answer applies. */
	private String translation;
	/** Vietnamese literal translation of {@code answer} (the correct sentence). */
	private String translationVi;
}
