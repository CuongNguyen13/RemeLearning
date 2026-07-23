package com.remelearning.english.listening.domain;

/**
 * The three listening-comprehension question shapes: {@code MCQ} (main idea/detail/attitude,
 * 4 options), {@code KEYWORD} (fill in the specific word/phrase heard, scored by WER like
 * dictation), {@code OPEN} (short free-text response, graded 0..1 by an LLM against a model answer).
 */
public enum ListeningQuestionType {
	MCQ,
	KEYWORD,
	OPEN
}
