package com.remelearning.english.pronunciation.domain;

/**
 * Classification of a pronunciation weak point: which aspect of spoken English
 * the learner keeps getting wrong (a vowel/consonant sound, word stress, ...).
 */
public enum PronunciationType {
	VOWEL,
	CONSONANT,
	STRESS,
	INTONATION,
	LINKING,
	RHYTHM,
	OTHER
}
