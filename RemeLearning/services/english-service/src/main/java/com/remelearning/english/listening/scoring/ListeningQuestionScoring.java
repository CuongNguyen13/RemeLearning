package com.remelearning.english.listening.scoring;

import com.remelearning.english.dictation.scoring.DictationScorer;
import com.remelearning.english.listening.domain.ListeningQuestionItem;
import com.remelearning.english.listening.domain.ListeningQuestionType;

/**
 * Pure, stateless scoring for the two "closed" listening-question types: {@code MCQ} (exact
 * normalized match against one option) and {@code KEYWORD} (word-error-rate accuracy via the same
 * {@link DictationScorer} dictation uses, since "did the learner catch this exact word/phrase by
 * ear" is the same problem as grading a dictation attempt). {@code OPEN} questions are graded by
 * an LLM instead (see {@code ListeningLearnServiceImpl}), so they have no place in this pure class.
 */
public final class ListeningQuestionScoring {

	/** A KEYWORD sub-score at/above this is treated as "correct" for the weak-point feed and result badge. */
	public static final double CORRECT_THRESHOLD = 0.6;

	private ListeningQuestionScoring() {
	}

	/** @throws IllegalArgumentException if {@code item} is an OPEN question. */
	public static double scoreClosed(ListeningQuestionItem item, String submitted) {
		return switch (item.getType()) {
			case MCQ -> normalize(item.getAnswer()).equals(normalize(submitted)) ? 1.0 : 0.0;
			case KEYWORD -> DictationScorer.score(item.getAnswer(), submitted == null ? "" : submitted).getAccuracy();
			case OPEN -> throw new IllegalArgumentException("OPEN questions must be graded by the LLM, not scoreClosed");
		};
	}

	private static String normalize(String value) {
		return value == null ? "" : value.trim().toLowerCase().replaceAll("\\s+", " ");
	}
}
