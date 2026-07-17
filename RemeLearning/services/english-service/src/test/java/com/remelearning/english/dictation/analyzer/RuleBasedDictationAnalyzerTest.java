package com.remelearning.english.dictation.analyzer;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class RuleBasedDictationAnalyzerTest {

	private final RuleBasedDictationAnalyzer analyzer = new RuleBasedDictationAnalyzer();

	// With misses, suggestions name the missed words and there is one practice sentence per word.
	@Test
	void analyzeAttemptProducesSuggestionsAndPerWordPracticeSentences() {
		DictationAnalysis analysis = analyzer.analyzeAttempt("reference", List.of("reluctant", "admit"));

		assertThat(analysis.getSuggestions()).isNotEmpty();
		assertThat(analysis.getSuggestions().get(0)).contains("reluctant").contains("admit");
		assertThat(analysis.getPracticeSentences()).hasSize(2);
		assertThat(analysis.getPracticeSentences().get(0)).contains("reluctant");
	}

	// With no misses, it still returns a (praise) suggestion and a generic practice sentence - never empty.
	@Test
	void analyzeAttemptHandlesNoMisses() {
		DictationAnalysis analysis = analyzer.analyzeAttempt("reference", List.of());

		assertThat(analysis.getSuggestions()).isNotEmpty();
		assertThat(analysis.getPracticeSentences()).isNotEmpty();
	}

	// generatePracticeSentences yields one sentence per requested word.
	@Test
	void generatePracticeSentencesOnePerWord() {
		assertThat(analyzer.generatePracticeSentences(List.of("alpha", "beta"))).hasSize(2);
	}
}
