package com.remelearning.english.listening.generator;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.remelearning.english.listening.domain.ListeningQuestionType;
import com.remelearning.english.listening.dto.ListeningAttemptQuestionResultDto;
import com.remelearning.english.listening.library.domain.ListeningLibraryAttemptAnswer;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ListeningMistakeAnalyzerTest {

	private final ObjectMapper objectMapper = new ObjectMapper();

	@Test
	void extractMissedTopicsReturnsEmptyWhenAllAnswersCorrect() {
		String resultsJson = toJson(List.of(
				ListeningAttemptQuestionResultDto.builder().index(0).prompt("p1")
						.correctAnswer("A flight departure").correct(true).subScore(1.0)
						.type(ListeningQuestionType.MCQ).build(),
				ListeningAttemptQuestionResultDto.builder().index(1).prompt("p2")
						.correctAnswer("departing").correct(true).subScore(1.0)
						.type(ListeningQuestionType.KEYWORD).build()));

		List<String> missed = ListeningMistakeAnalyzer.extractMissedTopics(resultsJson, "Travel");

		assertThat(missed).isEmpty();
	}

	@Test
	void extractMissedTopicsReturnsDistinctCorrectAnswersOfWrongKeywordAndMcqQuestions() {
		String resultsJson = toJson(List.of(
				ListeningAttemptQuestionResultDto.builder().index(0).prompt("p1")
						.correctAnswer("A flight departure").correct(false).subScore(0.0)
						.type(ListeningQuestionType.MCQ).build(),
				ListeningAttemptQuestionResultDto.builder().index(1).prompt("p2")
						.correctAnswer("departing").correct(true).subScore(1.0)
						.type(ListeningQuestionType.KEYWORD).build()));

		List<String> missed = ListeningMistakeAnalyzer.extractMissedTopics(resultsJson, "Travel");

		assertThat(missed).containsExactly("A flight departure");
	}

	@Test
	void extractMissedTopicsDeduplicatesTheSameCorrectAnswerAcrossMultipleWrongQuestions() {
		String resultsJson = toJson(List.of(
				ListeningAttemptQuestionResultDto.builder().index(0).prompt("p1")
						.correctAnswer("departing").correct(false).subScore(0.0)
						.type(ListeningQuestionType.KEYWORD).build(),
				ListeningAttemptQuestionResultDto.builder().index(1).prompt("p2")
						.correctAnswer("departing").correct(false).subScore(0.2)
						.type(ListeningQuestionType.KEYWORD).build()));

		List<String> missed = ListeningMistakeAnalyzer.extractMissedTopics(resultsJson, "Travel");

		assertThat(missed).containsExactly("departing");
	}

	@Test
	void extractMissedTopicsUsesTopicNameInsteadOfCorrectAnswerForMissedOpenQuestions() {
		String resultsJson = toJson(List.of(
				ListeningAttemptQuestionResultDto.builder().index(0).prompt("p1")
						.correctAnswer("The speaker is worried the flight will be delayed because of the storm.")
						.correct(false).subScore(0.2).type(ListeningQuestionType.OPEN).build()));

		List<String> missed = ListeningMistakeAnalyzer.extractMissedTopics(resultsJson, "Travel");

		assertThat(missed).containsExactly("Travel");
	}

	@Test
	void extractMissedTopicsKeepsUsingCorrectAnswerForKeywordAndMcqAlongsideAMissedOpenQuestion() {
		String resultsJson = toJson(List.of(
				ListeningAttemptQuestionResultDto.builder().index(0).prompt("p1")
						.correctAnswer("departing").correct(false).subScore(0.0)
						.type(ListeningQuestionType.KEYWORD).build(),
				ListeningAttemptQuestionResultDto.builder().index(1).prompt("p2")
						.correctAnswer("The speaker feels relieved the trip is finally over.")
						.correct(false).subScore(0.1).type(ListeningQuestionType.OPEN).build()));

		List<String> missed = ListeningMistakeAnalyzer.extractMissedTopics(resultsJson, "Travel");

		assertThat(missed).containsExactly("departing", "Travel");
	}

	@Test
	void extractMissedTopicsTreatsNullTypeAsNonOpenForBackwardCompatibility() {
		String resultsJson = toJson(List.of(
				ListeningAttemptQuestionResultDto.builder().index(0).prompt("p1")
						.correctAnswer("A flight departure").correct(false).subScore(0.0).build()));

		List<String> missed = ListeningMistakeAnalyzer.extractMissedTopics(resultsJson, "Travel");

		assertThat(missed).containsExactly("A flight departure");
	}

	@Test
	void hasAnyMissedQuestionReturnsFalseWhenAllAnswersCorrect() {
		ListeningLibraryAttemptAnswer answer = new ListeningLibraryAttemptAnswer();
		answer.setQuestionId(1L);
		answer.setIsCorrect(true);

		boolean hasMistakes = ListeningMistakeAnalyzer.hasAnyMissedQuestion(List.of(answer));

		assertThat(hasMistakes).isFalse();
	}

	@Test
	void hasAnyMissedQuestionReturnsTrueWhenAtLeastOneAnswerIsWrong() {
		ListeningLibraryAttemptAnswer correctAnswer = new ListeningLibraryAttemptAnswer();
		correctAnswer.setQuestionId(1L);
		correctAnswer.setIsCorrect(true);
		ListeningLibraryAttemptAnswer wrongAnswer = new ListeningLibraryAttemptAnswer();
		wrongAnswer.setQuestionId(2L);
		wrongAnswer.setIsCorrect(false);

		boolean hasMistakes = ListeningMistakeAnalyzer.hasAnyMissedQuestion(List.of(correctAnswer, wrongAnswer));

		assertThat(hasMistakes).isTrue();
	}

	@Test
	void hasAnyMissedQuestionTreatsNullIsCorrectAsMissed() {
		ListeningLibraryAttemptAnswer answer = new ListeningLibraryAttemptAnswer();
		answer.setQuestionId(1L);
		answer.setIsCorrect(null);

		boolean hasMistakes = ListeningMistakeAnalyzer.hasAnyMissedQuestion(List.of(answer));

		assertThat(hasMistakes).isTrue();
	}

	private String toJson(Object value) {
		try {
			return objectMapper.writeValueAsString(value);
		} catch (Exception ex) {
			throw new RuntimeException(ex);
		}
	}
}
