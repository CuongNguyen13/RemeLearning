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
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Pure diff logic behind the "generate AI practice targeting a past attempt's mistakes" feature
 * (both the "học thường" learn flow and the Thư viện/library flow): given the questions a learner
 * was asked and what they actually submitted, returns the distinct tags of every question they got
 * wrong, so the caller can hand that list straight to {@link GrammarPracticeGenerator#generate}.
 * No mapper/service dependency - both methods are plain functions over already-loaded JSON/DTOs so
 * they're unit-testable without mocks.
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

	// Library flow: a Grammar Library session's questions are scoped to one topic and carry no
	// explicit grammar-rule tag of their own (unlike the learn flow's GrammarQuestionItem), so each
	// question's own prompt is used as the best available per-question identifier of what was
	// missed - it's what the generator prompt shows the LLM to steer generation away from repeating
	// the same mistake. Every answer whose questionRef no longer matches a question in the session
	// (shouldn't normally happen) is skipped rather than failing the whole request.
	public static List<String> extractMissedRulesFromSession(String questionsJson, List<GrammarLibrarySessionAnswer> answers) {
		List<GrammarLibrarySessionQuestion> questions = readValue(questionsJson, new TypeReference<List<GrammarLibrarySessionQuestion>>() { });
		Map<String, String> promptByRef = questions.stream()
				.collect(Collectors.toMap(GrammarLibrarySessionQuestion::getQuestionRef,
						GrammarLibrarySessionQuestion::getPrompt, (first, second) -> first));

		Set<String> missedPrompts = new LinkedHashSet<>();
		for (GrammarLibrarySessionAnswer answer : answers) {
			if (answer.isCorrect()) {
				continue;
			}
			String prompt = promptByRef.get(answer.getQuestionRef());
			if (prompt != null) {
				missedPrompts.add(prompt);
			}
		}
		return new ArrayList<>(missedPrompts);
	}

	private static <T> T readValue(String json, TypeReference<T> typeReference) {
		try {
			return OBJECT_MAPPER.readValue(json, typeReference);
		} catch (JsonProcessingException ex) {
			throw new IllegalStateException("Failed to deserialize grammar mistake-analysis input", ex);
		}
	}
}
