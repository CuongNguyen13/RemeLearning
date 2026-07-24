package com.remelearning.english.listening.generator;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.remelearning.english.listening.dto.ListeningAttemptQuestionResultDto;
import com.remelearning.english.listening.library.domain.ListeningLibraryAttemptAnswer;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Pure diff logic behind the "generate AI practice targeting a past attempt's mistakes" feature
 * for listening (both the "học thường" learn flow and the Thư viện/library flow) - mirrors
 * {@code GrammarMistakeAnalyzer}. The learn flow's persisted {@code resultsJson} (see
 * {@link ListeningAttemptQuestionResultDto}) carries only {@code prompt}/{@code correctAnswer}/
 * {@code explanation} per question - no per-question "topic"/"skill" tag the way the source
 * {@link com.remelearning.english.listening.domain.ListeningQuestionItem} does - so
 * {@link #extractMissedTopics} uses each missed question's {@code correctAnswer} as the retry
 * target text: for KEYWORD questions this literally is the missed keyword, and for MCQ/OPEN
 * questions it's the correct option/model-answer text, which still gives
 * {@link LlmListeningPracticeGenerator} meaningful content to naturally reuse in a fresh passage.
 * The library flow has no per-question taxonomy at all (a Section is scoped to one topic already,
 * same as Grammar Library), so it only reports whether anything was missed at all; the caller
 * builds the single-element target-keywords list itself from the section's topic name (see
 * {@code ListeningLibraryServiceImpl.generatePracticeFromSection}). No mapper/service dependency -
 * both methods are plain functions over already-loaded JSON/DTOs so they're unit-testable without
 * mocks.
 */
public final class ListeningMistakeAnalyzer {

	private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

	private ListeningMistakeAnalyzer() {
	}

	// Learn flow: re-reads the attempt's already-graded resultsJson (grading itself is never
	// redone here - the stored `correct` flag is exactly what the learner saw at submit time) and
	// collects the distinct correctAnswer of every question that came back incorrect, preserving
	// first-seen order.
	public static List<String> extractMissedTopics(String resultsJson) {
		List<ListeningAttemptQuestionResultDto> results = readValue(
				resultsJson, new TypeReference<List<ListeningAttemptQuestionResultDto>>() { });

		Set<String> missedTopics = new LinkedHashSet<>();
		for (ListeningAttemptQuestionResultDto result : results) {
			if (!result.isCorrect() && result.getCorrectAnswer() != null) {
				missedTopics.add(result.getCorrectAnswer());
			}
		}
		return new ArrayList<>(missedTopics);
	}

	// Library flow: a Listening Library section is scoped to exactly one topic and its questions
	// carry no per-question topic tag of their own, so there is no meaningful per-question topic
	// to diff out here - every wrong answer in the attempt is missing the SAME topic. This only
	// needs to answer whether the attempt had any mistake at all; the caller is responsible for
	// turning that into a single-element target-keywords list built from the section's topic name.
	public static boolean hasAnyMissedQuestion(List<ListeningLibraryAttemptAnswer> answers) {
		for (ListeningLibraryAttemptAnswer answer : answers) {
			if (!Boolean.TRUE.equals(answer.getIsCorrect())) {
				return true;
			}
		}
		return false;
	}

	private static <T> T readValue(String json, TypeReference<T> typeReference) {
		try {
			return OBJECT_MAPPER.readValue(json, typeReference);
		} catch (JsonProcessingException ex) {
			throw new IllegalStateException("Failed to deserialize listening mistake-analysis input", ex);
		}
	}
}
