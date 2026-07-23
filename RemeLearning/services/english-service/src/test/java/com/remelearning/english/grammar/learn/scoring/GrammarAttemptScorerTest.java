package com.remelearning.english.grammar.learn.scoring;

import com.remelearning.english.grammar.learn.domain.GrammarQuestionItem;
import com.remelearning.english.grammar.learn.domain.GrammarQuestionType;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class GrammarAttemptScorerTest {

	@Test
	void scoresExactAndCaseInsensitiveMatchesAsCorrect() {
		List<GrammarQuestionItem> items = List.of(
				GrammarQuestionItem.builder().targetRule("past simple").type(GrammarQuestionType.FILL_TENSE)
						.answer("She went to school yesterday.").build(),
				GrammarQuestionItem.builder().targetRule("passive voice").type(GrammarQuestionType.TRANSFORM)
						.answer("The meal was cooked by the chef.").build());

		GrammarScoreResult result = GrammarAttemptScorer.score(items,
				List.of("She Went to school yesterday", "wrong"));

		assertThat(result.getAccuracy()).isEqualTo(0.5);
		assertThat(result.getPerQuestionCorrect()).containsExactly(true, false);
	}

	@Test
	void treatsMissingAnswerAsIncorrectRatherThanThrowing() {
		List<GrammarQuestionItem> items = List.of(
				GrammarQuestionItem.builder().targetRule("present perfect").type(GrammarQuestionType.ERROR_CORRECTION)
						.answer("She has lived here for 10 years.").build());

		GrammarScoreResult result = GrammarAttemptScorer.score(items, List.of());

		assertThat(result.getAccuracy()).isEqualTo(0.0);
		assertThat(result.getPerQuestionCorrect()).containsExactly(false);
	}

	@Test
	void errorCorrectionIgnoresTrailingPunctuationDifference() {
		List<GrammarQuestionItem> items = List.of(
				GrammarQuestionItem.builder().targetRule("subject-verb agreement").type(GrammarQuestionType.ERROR_CORRECTION)
						.answer("He plays football every day.").build());

		GrammarScoreResult result = GrammarAttemptScorer.score(items, List.of("He plays football every day"));

		assertThat(result.getAccuracy()).isEqualTo(1.0);
	}
}
