package com.remelearning.english.listening.scoring;

import com.remelearning.english.listening.domain.ListeningQuestionItem;
import com.remelearning.english.listening.domain.ListeningQuestionType;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ListeningQuestionScoringTest {

	@Test
	void mcqScoresExactNormalizedMatchAsOne() {
		ListeningQuestionItem item = ListeningQuestionItem.builder()
				.type(ListeningQuestionType.MCQ).answer("A flight departure").build();

		assertThat(ListeningQuestionScoring.scoreClosed(item, "A Flight Departure")).isEqualTo(1.0);
		assertThat(ListeningQuestionScoring.scoreClosed(item, "A train delay")).isEqualTo(0.0);
	}

	@Test
	void keywordScoresPartialCreditViaWordErrorRate() {
		ListeningQuestionItem item = ListeningQuestionItem.builder()
				.type(ListeningQuestionType.KEYWORD).answer("departure lounge").build();

		double exact = ListeningQuestionScoring.scoreClosed(item, "departure lounge");
		double partial = ListeningQuestionScoring.scoreClosed(item, "departure");
		double none = ListeningQuestionScoring.scoreClosed(item, "");

		assertThat(exact).isEqualTo(1.0);
		assertThat(partial).isBetween(0.0, 1.0).isGreaterThan(none);
	}

	@Test
	void openQuestionsAreRejectedByScoreClosed() {
		ListeningQuestionItem item = ListeningQuestionItem.builder().type(ListeningQuestionType.OPEN).build();

		assertThatThrownBy(() -> ListeningQuestionScoring.scoreClosed(item, "anything"))
				.isInstanceOf(IllegalArgumentException.class);
	}
}
