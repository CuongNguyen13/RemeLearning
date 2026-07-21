package com.remelearning.english.dictation.analyzer;

import com.remelearning.english.dictation.dto.WordDiffDto;
import com.remelearning.english.dictation.dto.WordDiffTag;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class RuleBasedDictationAnalyzerTest {

	private final RuleBasedDictationAnalyzer analyzer = new RuleBasedDictationAnalyzer();

	private static WordDiffDto miss(String expected, String actual) {
		return WordDiffDto.builder().tag(WordDiffTag.MISSING).expectedWord(expected).actualWord(actual).build();
	}

	// A dropped inflection (-ed) classifies as GRAMMAR, not vocabulary.
	@Test
	void analyzeAttemptClassifiesDroppedInflectionAsGrammar() {
		DictationAnalysis analysis = analyzer.analyzeAttempt("She finished it.", "She finish it.",
				List.of(miss("finished", "finish")));

		assertThat(analysis.getErrorTable()).hasSize(1);
		assertThat(analysis.getErrorTable().get(0).getCategory()).isEqualTo(DictationErrorCategory.GRAMMAR);
		assertThat(analysis.getRootCauses()).extracting(DictationRootCauseGroup::getCategory)
				.containsExactly(DictationErrorCategory.GRAMMAR);
	}

	// A missed common function word classifies as PHONOLOGY (weak/connected-speech form).
	@Test
	void analyzeAttemptClassifiesMissedFunctionWordAsPhonology() {
		DictationAnalysis analysis = analyzer.analyzeAttempt("Give it to her.", "Give it her.",
				List.of(miss("to", null)));

		assertThat(analysis.getErrorTable().get(0).getCategory()).isEqualTo(DictationErrorCategory.PHONOLOGY);
	}

	// Anything else (an unrelated content word) defaults to LEXICON.
	@Test
	void analyzeAttemptClassifiesUnrelatedContentWordAsLexicon() {
		DictationAnalysis analysis = analyzer.analyzeAttempt("She was reluctant to admit it.",
				"She was to admit it.", List.of(miss("reluctant", null)));

		assertThat(analysis.getErrorTable().get(0).getCategory()).isEqualTo(DictationErrorCategory.LEXICON);
		assertThat(analysis.getPracticeSentences()).hasSize(1);
		assertThat(analysis.getPracticeSentences().get(0)).contains("reluctant");
	}

	// rootCauses only ever contains categories that actually occurred.
	@Test
	void analyzeAttemptOmitsRootCauseCategoriesWithNoMisses() {
		DictationAnalysis analysis = analyzer.analyzeAttempt("She finished it.", "She finish it.",
				List.of(miss("finished", "finish")));

		assertThat(analysis.getRootCauses()).hasSize(1);
	}

	// With no misses, it still returns a (praise) advice and a generic practice sentence - never empty.
	@Test
	void analyzeAttemptHandlesNoMisses() {
		DictationAnalysis analysis = analyzer.analyzeAttempt("reference", "reference", List.of());

		assertThat(analysis.getActionAdvice()).isNotEmpty();
		assertThat(analysis.getErrorTable()).isEmpty();
		assertThat(analysis.getRootCauses()).isEmpty();
		assertThat(analysis.getPracticeSentences()).isNotEmpty();
	}

	// generatePracticeSentences yields one sentence per requested word.
	@Test
	void generatePracticeSentencesOnePerWord() {
		assertThat(analyzer.generatePracticeSentences(List.of("alpha", "beta"))).hasSize(2);
	}
}
