package com.remelearning.english.grammar.learn.generator;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.remelearning.english.grammar.learn.domain.GrammarQuestionItem;
import com.remelearning.english.grammar.learn.domain.GrammarQuestionType;
import com.remelearning.english.grammar.library.domain.GrammarLibrarySessionAnswer;
import com.remelearning.english.grammar.library.domain.GrammarLibrarySessionQuestion;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class GrammarMistakeAnalyzerTest {

	private final ObjectMapper objectMapper = new ObjectMapper();

	@Test
	void extractMissedRulesReturnsEmptyWhenAllAnswersCorrect() {
		String itemsJson = toJson(List.of(
				GrammarQuestionItem.builder().targetRule("past perfect").type(GrammarQuestionType.FILL_TENSE)
						.prompt("p1").answer("had left").build(),
				GrammarQuestionItem.builder().targetRule("passive voice").type(GrammarQuestionType.MCQ)
						.prompt("p2").answer("was cooked").build()));
		String answersJson = toJson(List.of("had left", "was cooked"));

		List<String> missed = GrammarMistakeAnalyzer.extractMissedRules(itemsJson, answersJson);

		assertThat(missed).isEmpty();
	}

	@Test
	void extractMissedRulesReturnsDistinctRuleTagsOfWrongAnswers() {
		String itemsJson = toJson(List.of(
				GrammarQuestionItem.builder().targetRule("past perfect").type(GrammarQuestionType.FILL_TENSE)
						.prompt("p1").answer("had left").build(),
				GrammarQuestionItem.builder().targetRule("passive voice").type(GrammarQuestionType.MCQ)
						.prompt("p2").answer("was cooked").build()));
		String answersJson = toJson(List.of("wrong answer", "was cooked"));

		List<String> missed = GrammarMistakeAnalyzer.extractMissedRules(itemsJson, answersJson);

		assertThat(missed).containsExactly("past perfect");
	}

	@Test
	void extractMissedRulesDeduplicatesTheSameRuleAcrossMultipleWrongQuestions() {
		String itemsJson = toJson(List.of(
				GrammarQuestionItem.builder().targetRule("past perfect").type(GrammarQuestionType.FILL_TENSE)
						.prompt("p1").answer("had left").build(),
				GrammarQuestionItem.builder().targetRule("past perfect").type(GrammarQuestionType.ERROR_CORRECTION)
						.prompt("p2").answer("had gone").build()));
		String answersJson = toJson(List.of("wrong 1", "wrong 2"));

		List<String> missed = GrammarMistakeAnalyzer.extractMissedRules(itemsJson, answersJson);

		assertThat(missed).containsExactly("past perfect");
	}

	@Test
	void hasAnyMissedQuestionReturnsFalseWhenAllAnswersCorrect() {
		String questionsJson = toJson(List.of(
				GrammarLibrarySessionQuestion.builder().questionRef("q-1").type(GrammarQuestionType.MCQ)
						.prompt("She ___ every day.").answer("works").build()));
		List<GrammarLibrarySessionAnswer> answers = List.of(
				GrammarLibrarySessionAnswer.builder().questionRef("q-1").submittedAnswer("works").correct(true).build());

		boolean hasMistakes = GrammarMistakeAnalyzer.hasAnyMissedQuestion(questionsJson, answers);

		assertThat(hasMistakes).isFalse();
	}

	@Test
	void hasAnyMissedQuestionReturnsTrueWhenAtLeastOneAnswerIsWrong() {
		String questionsJson = toJson(List.of(
				GrammarLibrarySessionQuestion.builder().questionRef("q-1").type(GrammarQuestionType.MCQ)
						.prompt("She ___ every day.").answer("works").build(),
				GrammarLibrarySessionQuestion.builder().questionRef("q-2").type(GrammarQuestionType.FILL_TENSE)
						.prompt("They (go) home yesterday.").answer("went").build()));
		List<GrammarLibrarySessionAnswer> answers = List.of(
				GrammarLibrarySessionAnswer.builder().questionRef("q-1").submittedAnswer("work").correct(false).build(),
				GrammarLibrarySessionAnswer.builder().questionRef("q-2").submittedAnswer("went").correct(true).build());

		boolean hasMistakes = GrammarMistakeAnalyzer.hasAnyMissedQuestion(questionsJson, answers);

		assertThat(hasMistakes).isTrue();
	}

	@Test
	void hasAnyMissedQuestionIgnoresAnswersForQuestionRefsNotInTheSession() {
		String questionsJson = toJson(List.of(
				GrammarLibrarySessionQuestion.builder().questionRef("q-1").type(GrammarQuestionType.MCQ)
						.prompt("She ___ every day.").answer("works").build()));
		List<GrammarLibrarySessionAnswer> answers = List.of(
				GrammarLibrarySessionAnswer.builder().questionRef("q-unknown").submittedAnswer("work").correct(false).build());

		boolean hasMistakes = GrammarMistakeAnalyzer.hasAnyMissedQuestion(questionsJson, answers);

		assertThat(hasMistakes).isFalse();
	}

	private String toJson(Object value) {
		try {
			return objectMapper.writeValueAsString(value);
		} catch (Exception ex) {
			throw new RuntimeException(ex);
		}
	}
}
