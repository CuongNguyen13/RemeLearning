package com.remelearning.english.vocabulary.learn.domain;

/**
 * The three AI-generated exercise shapes for one target word: {@code CLOZE} (fill the blank in a
 * context sentence), {@code MCQ} (pick the correct word for the blank among options), and
 * {@code MATCHING} (pick the correct meaning among options - a simplified one-word-at-a-time take
 * on "match word to meaning" rather than a batch drag-and-drop UI, so scoring stays uniform across
 * all three types).
 */
public enum VocabQuestionType {
	CLOZE,
	MCQ,
	MATCHING
}
