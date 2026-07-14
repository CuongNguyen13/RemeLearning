package com.remelearning.english.vocabulary.classifier;

import com.remelearning.english.vocabulary.domain.VocabularyType;

/**
 * Classifies a raw vocabulary label (e.g. "word: reluctant", "give up") into a {@link VocabularyType}.
 * Swappable: today rule-based ({@link RuleBasedVocabularyClassifier}); an LLM-backed implementation
 * can replace it later behind this same interface, mirroring the {@code MistakeAnalyzer} pattern
 * used in ai-service's rule_based_analyzer.py.
 */
public interface VocabularyClassifier {

	VocabularyType classify(String label);
}
