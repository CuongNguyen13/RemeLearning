package com.remelearning.english.vocabulary.learn.scoring;

import com.remelearning.english.vocabulary.learn.domain.VocabQuestionItem;
import com.remelearning.english.vocabulary.learn.domain.VocabQuestionType;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class VocabAttemptScorerTest {

	@Test
	void scoresExactAndCaseInsensitiveMatchesAsCorrect() {
		List<VocabQuestionItem> items = List.of(
				VocabQuestionItem.builder().targetWord("reluctant").type(VocabQuestionType.CLOZE).answer("reluctant").build(),
				VocabQuestionItem.builder().targetWord("brief").type(VocabQuestionType.MCQ).answer("brief").build());

		VocabScoreResult result = VocabAttemptScorer.score(items, List.of("Reluctant", "wrong"));

		assertThat(result.getAccuracy()).isEqualTo(0.5);
		assertThat(result.getPerQuestionCorrect()).containsExactly(true, false);
	}

	@Test
	void treatsMissingAnswerAsIncorrectRatherThanThrowing() {
		List<VocabQuestionItem> items = List.of(
				VocabQuestionItem.builder().targetWord("brief").type(VocabQuestionType.CLOZE).answer("brief").build());

		VocabScoreResult result = VocabAttemptScorer.score(items, List.of());

		assertThat(result.getAccuracy()).isEqualTo(0.0);
		assertThat(result.getPerQuestionCorrect()).containsExactly(false);
	}

	@Test
	void emptyItemListScoresZeroWithoutDividingByZero() {
		VocabScoreResult result = VocabAttemptScorer.score(List.of(), List.of());

		assertThat(result.getAccuracy()).isEqualTo(0.0);
		assertThat(result.getPerQuestionCorrect()).isEmpty();
	}
}
