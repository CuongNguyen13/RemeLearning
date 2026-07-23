package com.remelearning.english.grammar.library.generator;

import com.remelearning.english.grammar.learn.domain.GrammarQuestionType;

import java.util.List;

/** One AI-generated question, before it is persisted (pool question) or wrapped into a session snapshot (retry question). */
public record GrammarLibraryQuestionSeed(
		GrammarQuestionType type,
		String prompt,
		List<String> options,
		String answer,
		String explanationVi,
		String translationVi) {
}
