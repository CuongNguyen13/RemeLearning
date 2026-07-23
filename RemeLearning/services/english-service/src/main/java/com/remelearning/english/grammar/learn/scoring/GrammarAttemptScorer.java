package com.remelearning.english.grammar.learn.scoring;

import com.remelearning.common.scoring.ExactMatchScoreResult;
import com.remelearning.common.scoring.ExactMatchScorer;
import com.remelearning.english.grammar.learn.domain.GrammarQuestionItem;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Pure, stateless scoring for one grammar-practice attempt, delegating the actual
 * normalize-and-compare work to {@link ExactMatchScorer} (shared with vocabulary practice, which
 * uses the same exact-match approach minus the trailing-punctuation strip).
 *
 * <p>ponytail: for {@code ERROR_CORRECTION}/{@code TRANSFORM}/{@code FILL_TENSE}, a grammatically
 * correct rewrite that differs from the AI-generated sample answer (different word order,
 * synonym, etc.) will be marked wrong - exact-normalized-match is a deliberate simplification for
 * this stage, not an oversight. Upgrade path: score those three types via an LLM
 * ("is this sentence grammatically equivalent to the reference answer?") behind this same
 * {@code score} entry point when exact-match false negatives turn out to matter in practice.
 */
public final class GrammarAttemptScorer {

	private GrammarAttemptScorer() {
	}

	public static GrammarScoreResult score(List<GrammarQuestionItem> items, List<String> answers) {
		List<String> correctAnswers = items.stream().map(GrammarQuestionItem::getAnswer).collect(Collectors.toList());
		ExactMatchScoreResult result = ExactMatchScorer.score(correctAnswers, answers, true);
		return GrammarScoreResult.builder().accuracy(result.getAccuracy())
				.perQuestionCorrect(result.getPerQuestionCorrect()).build();
	}
}
