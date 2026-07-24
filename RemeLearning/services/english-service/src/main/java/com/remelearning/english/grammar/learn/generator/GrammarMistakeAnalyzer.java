package com.remelearning.english.grammar.learn.generator;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.remelearning.english.grammar.learn.domain.GrammarQuestionItem;
import com.remelearning.english.grammar.learn.scoring.GrammarAttemptScorer;
import com.remelearning.english.grammar.learn.scoring.GrammarScoreResult;
import com.remelearning.english.grammar.library.domain.GrammarLibrarySessionAnswer;
import com.remelearning.english.grammar.library.domain.GrammarLibrarySessionQuestion;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Pure diff logic behind the "generate AI practice targeting a past attempt's mistakes" feature
 * (both the "học thường" learn flow and the Thư viện/library flow): given the questions a learner
 * was asked and what they actually submitted, figures out what was missed. The learn flow has a
 * per-question rule tag ({@link GrammarQuestionItem#getTargetRule()}) so it returns the distinct
 * tags of every wrong question, ready to hand straight to {@link GrammarPracticeGenerator#generate}.
 * The library flow has no such per-question tag - a session is scoped to one topic/rule already -
 * so it only reports whether anything was missed at all; the caller builds the target-rules list
 * itself from the session's topic name. No mapper/service dependency - both methods are plain
 * functions over already-loaded JSON/DTOs so they're unit-testable without mocks.
 */
public final class GrammarMistakeAnalyzer {

	private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

	private GrammarMistakeAnalyzer() {
	}

	// Learn flow: re-scores the attempt's stored questions against the stored submitted answers
	// with the exact same GrammarAttemptScorer the original attempt was graded with (so "wrong" here
	// means exactly what it meant at submit time), then collects the distinct targetRule of every
	// question that came back incorrect, preserving first-seen order.
	public static List<String> extractMissedRules(String itemsJson, String answersJson) {
		List<GrammarQuestionItem> items = readValue(itemsJson, new TypeReference<List<GrammarQuestionItem>>() { });
		List<String> answers = readValue(answersJson, new TypeReference<List<String>>() { });
		GrammarScoreResult score = GrammarAttemptScorer.score(items, answers);

		Set<String> missedRules = new LinkedHashSet<>();
		for (int i = 0; i < items.size(); i++) {
			if (!Boolean.TRUE.equals(score.getPerQuestionCorrect().get(i))) {
				missedRules.add(items.get(i).getTargetRule());
			}
		}
		return new ArrayList<>(missedRules);
	}

	// Library flow: a Grammar Library session is scoped to exactly one topic (one grammar rule
	// concept) and its questions carry no per-question rule tag of their own (unlike the learn
	// flow's GrammarQuestionItem), so there is no meaningful per-question "rule" to diff out here -
	// every wrong question in the session is missing the SAME topic's rule. This only needs to
	// answer whether the session had any mistake at all; the caller is responsible for turning that
	// into a single-element target-rules list built from the session's topic name (see
	// GrammarLibraryServiceImpl.generatePracticeFromSession). Every answer whose questionRef no
	// longer matches a question in the session (shouldn't normally happen) is skipped rather than
	// counting as a miss.
	public static boolean hasAnyMissedQuestion(String questionsJson, List<GrammarLibrarySessionAnswer> answers) {
		List<GrammarLibrarySessionQuestion> questions = readValue(questionsJson, new TypeReference<List<GrammarLibrarySessionQuestion>>() { });
		Set<String> questionRefs = questions.stream()
				.map(GrammarLibrarySessionQuestion::getQuestionRef)
				.collect(Collectors.toSet());

		for (GrammarLibrarySessionAnswer answer : answers) {
			if (!answer.isCorrect() && questionRefs.contains(answer.getQuestionRef())) {
				return true;
			}
		}
		return false;
	}

	private static <T> T readValue(String json, TypeReference<T> typeReference) {
		try {
			return OBJECT_MAPPER.readValue(json, typeReference);
		} catch (JsonProcessingException ex) {
			throw new IllegalStateException("Failed to deserialize grammar mistake-analysis input", ex);
		}
	}
}
