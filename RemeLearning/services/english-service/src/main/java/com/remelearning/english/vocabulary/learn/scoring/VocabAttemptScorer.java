package com.remelearning.english.vocabulary.learn.scoring;

import com.remelearning.common.scoring.ExactMatchScoreResult;
import com.remelearning.common.scoring.ExactMatchScorer;
import com.remelearning.english.vocabulary.learn.domain.VocabQuestionItem;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Pure, stateless scoring for one vocabulary-practice attempt, delegating the actual
 * normalize-and-compare work to {@link ExactMatchScorer} (shared with grammar practice) - identical
 * comparison for all three question types (CLOZE/MCQ/MATCHING), since MCQ/MATCHING answers are just
 * one of the option strings.
 */
public final class VocabAttemptScorer {

	private VocabAttemptScorer() {
	}

	public static VocabScoreResult score(List<VocabQuestionItem> items, List<String> answers) {
		List<String> correctAnswers = items.stream().map(VocabQuestionItem::getAnswer).collect(Collectors.toList());
		ExactMatchScoreResult result = ExactMatchScorer.score(correctAnswers, answers, false);
		return VocabScoreResult.builder().accuracy(result.getAccuracy())
				.perQuestionCorrect(result.getPerQuestionCorrect()).build();
	}
}
