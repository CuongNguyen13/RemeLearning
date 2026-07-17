package com.remelearning.english.dictation.analyzer;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Default {@link DictationAnalyzer}: static templates, no LLM cost. Active unless
 * {@code dictation.analyzer.mode=llm} (see {@link LlmDictationAnalyzer}).
 */
@Component
@ConditionalOnProperty(prefix = "dictation.analyzer", name = "mode", havingValue = "rule-based", matchIfMissing = true)
public class RuleBasedDictationAnalyzer implements DictationAnalyzer {

	// Builds suggestions + one practice sentence per missed word from static templates.
	@Override
	public DictationAnalysis analyzeAttempt(String referenceText, List<String> missedWords) {
		return DictationAnalysis.builder()
				.suggestions(DictationAnalysisTemplates.suggestionsFor(missedWords))
				.practiceSentences(DictationAnalysisTemplates.practiceSentencesFor(missedWords))
				.build();
	}

	@Override
	public List<String> generatePracticeSentences(List<String> missedWords) {
		return DictationAnalysisTemplates.practiceSentencesFor(missedWords);
	}
}
