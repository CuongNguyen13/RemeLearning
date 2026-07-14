package com.remelearning.english.grammar.classifier;

import com.remelearning.english.grammar.domain.GrammarType;

/**
 * Classifies a raw grammar weak-point label (e.g. "Subject-verb agreement in third person",
 * "past simple tense") into a {@link GrammarType}. Swappable: today rule-based
 * ({@link RuleBasedGrammarClassifier}); {@link LlmGrammarClassifier} can replace it behind
 * this same interface, mirroring vocabulary's {@code VocabularyClassifier} pattern.
 */
public interface GrammarClassifier {

	GrammarType classify(String label);
}
