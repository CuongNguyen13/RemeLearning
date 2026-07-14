package com.remelearning.english.pronunciation.classifier;

import com.remelearning.english.pronunciation.domain.PronunciationType;

/**
 * Classifies a raw pronunciation weak-point label (e.g. "th sound confusion", "word stress on
 * 'photograph'") into a {@link PronunciationType}. Swappable: today rule-based
 * ({@link RuleBasedPronunciationClassifier}); {@link LlmPronunciationClassifier} can replace it
 * behind this same interface, mirroring vocabulary's {@code VocabularyClassifier} pattern.
 */
public interface PronunciationClassifier {

	PronunciationType classify(String label);
}
