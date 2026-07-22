package com.remelearning.english.vocabulary.library.scoring;

import com.remelearning.english.dictation.scoring.DictationScorer;
import com.remelearning.english.vocabulary.library.domain.SectionExerciseType;

/**
 * Pure, stateless scoring for the five Section QUIZ types gradeable without an LLM call: exact
 * normalized match for {@code MCQ}/{@code CLOZE}/{@code MATCHING}/{@code TRANSLATE_VI_TO_EN}, and
 * word-error-rate accuracy (same {@link DictationScorer} dictation/listening-keyword use) for
 * {@code LISTENING_DICTATION}. {@code TRANSLATE_EN_TO_VI} (free-text Vietnamese meaning) has no
 * place here - it's graded by an LLM instead (see {@code VocabularyLibraryServiceImpl}).
 */
public final class SectionAnswerScoring {

	/** A continuous sub-score at/above this is treated as "correct" for the queue/weak-point feed. */
	public static final double CORRECT_THRESHOLD = 0.7;

	private SectionAnswerScoring() {
	}

	/** @throws IllegalArgumentException if {@code type} is {@code TRANSLATE_EN_TO_VI} */
	public static double scoreClosed(SectionExerciseType type, String correctAnswer, String submitted) {
		return switch (type) {
			case MCQ, CLOZE, MATCHING, TRANSLATE_VI_TO_EN ->
					normalize(correctAnswer).equals(normalize(submitted)) ? 1.0 : 0.0;
			case LISTENING_DICTATION -> DictationScorer.score(correctAnswer, submitted == null ? "" : submitted).getAccuracy();
			case TRANSLATE_EN_TO_VI ->
					throw new IllegalArgumentException("TRANSLATE_EN_TO_VI must be graded by the LLM, not scoreClosed");
		};
	}

	private static String normalize(String value) {
		return value == null ? "" : value.trim().toLowerCase().replaceAll("\\s+", " ");
	}
}
