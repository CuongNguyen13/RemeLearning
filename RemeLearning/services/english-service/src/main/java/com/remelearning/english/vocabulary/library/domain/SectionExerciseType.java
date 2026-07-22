package com.remelearning.english.vocabulary.library.domain;

/**
 * The six exercise shapes a Section QUIZ card can take. {@code TRANSLATE_EN_TO_VI} is graded by an
 * LLM (free-text meaning); every other type is graded by exact/normalized match or word-error-rate.
 */
public enum SectionExerciseType {
	MCQ,
	CLOZE,
	MATCHING,
	LISTENING_DICTATION,
	TRANSLATE_EN_TO_VI,
	TRANSLATE_VI_TO_EN
}
