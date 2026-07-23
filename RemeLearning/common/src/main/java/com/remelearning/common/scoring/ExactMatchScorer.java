package com.remelearning.common.scoring;

import java.util.ArrayList;
import java.util.List;

/**
 * Pure, stateless scoring shared by grammar- and vocabulary-practice attempts: each question's
 * correct answer is compared, normalized (trim, collapse whitespace, lowercase, optionally drop a
 * trailing "." "!" or "?"), against the learner's submitted answer at the same index.
 *
 * <p>ponytail: exact-normalized-match is a deliberate simplification, not an oversight - see
 * callers for domain-specific upgrade notes (e.g. grammar's free-form question types would need an
 * LLM equivalence check instead of exact match).
 */
public final class ExactMatchScorer {

	private ExactMatchScorer() {
	}

	public static ExactMatchScoreResult score(List<String> correctAnswers, List<String> submittedAnswers,
			boolean stripTrailingPunctuation) {
		List<Boolean> perQuestionCorrect = new ArrayList<>();
		int correctCount = 0;
		for (int i = 0; i < correctAnswers.size(); i++) {
			String submitted = i < submittedAnswers.size() ? submittedAnswers.get(i) : null;
			boolean correct = normalize(correctAnswers.get(i), stripTrailingPunctuation)
					.equals(normalize(submitted, stripTrailingPunctuation));
			perQuestionCorrect.add(correct);
			if (correct) {
				correctCount++;
			}
		}
		double accuracy = correctAnswers.isEmpty() ? 0.0 : (double) correctCount / correctAnswers.size();
		return ExactMatchScoreResult.builder().accuracy(accuracy).perQuestionCorrect(perQuestionCorrect).build();
	}

	private static String normalize(String value, boolean stripTrailingPunctuation) {
		if (value == null) {
			return "";
		}
		String normalized = value.trim().toLowerCase().replaceAll("\\s+", " ");
		return stripTrailingPunctuation ? normalized.replaceAll("[.!?]+$", "") : normalized;
	}
}
