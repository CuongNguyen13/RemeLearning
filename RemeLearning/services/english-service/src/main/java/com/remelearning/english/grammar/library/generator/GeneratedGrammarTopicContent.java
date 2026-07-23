package com.remelearning.english.grammar.library.generator;

import com.remelearning.english.grammar.library.domain.GrammarLibraryExample;

import java.util.List;

/** The full AI-generated payload for one topic's theory page + its reusable question pool. */
public record GeneratedGrammarTopicContent(
		String explanationEn,
		String explanationVi,
		String illustrationText,
		List<GrammarLibraryExample> examples,
		List<GrammarLibraryQuestionSeed> questions) {
}
