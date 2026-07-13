package com.remelearning.vocabulary.domain;

/**
 * Classification of a vocabulary weak point: single-word part-of-speech vs.
 * multi-word units (phrasal verb / collocation / idiom).
 */
public enum VocabularyType {
	NOUN,
	VERB,
	ADJECTIVE,
	ADVERB,
	PHRASAL_VERB,
	COLLOCATION,
	IDIOM,
	OTHER
}
