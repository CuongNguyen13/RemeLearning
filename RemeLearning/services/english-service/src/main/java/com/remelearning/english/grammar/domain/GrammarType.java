package com.remelearning.english.grammar.domain;

/**
 * Classification of a grammar weak point: which grammatical rule the learner keeps
 * getting wrong (tense, agreement, articles, ...).
 */
public enum GrammarType {
	TENSE,
	SUBJECT_VERB_AGREEMENT,
	ARTICLE,
	PREPOSITION,
	WORD_ORDER,
	PLURAL,
	PUNCTUATION,
	OTHER
}
