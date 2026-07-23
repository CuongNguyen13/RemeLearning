package com.remelearning.english.grammar.learn.domain;

/**
 * The four AI-generated exercise shapes for one target grammar rule: {@code ERROR_CORRECTION}
 * (rewrite a sentence that has a grammar mistake), {@code FILL_TENSE} (put the bracketed verb
 * into the correct form/tense), {@code TRANSFORM} (rewrite a sentence per a given instruction,
 * keeping the meaning), and {@code MCQ} (pick the correct structure among options).
 */
public enum GrammarQuestionType {
	ERROR_CORRECTION,
	FILL_TENSE,
	TRANSFORM,
	MCQ
}
