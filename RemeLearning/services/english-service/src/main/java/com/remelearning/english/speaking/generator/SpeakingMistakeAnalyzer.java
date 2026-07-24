package com.remelearning.english.speaking.generator;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.List;

/**
 * Pure diff logic behind the "generate AI practice targeting a past attempt's mistakes" feature
 * for speaking (both the "học thường" learn flow and the Thư viện/library flow) - mirrors
 * {@code ListeningMistakeAnalyzer}/{@code GrammarMistakeAnalyzer}. Unlike listening's per-question
 * results (which need a KEYWORD/MCQ-vs-OPEN split to pick a usable retry-target text), speaking's
 * {@code weakPhonemesJson} (both {@code SpeakingAttemptDetailRow.weakPhonemesJson} for the learn
 * flow and Task 2's {@code SpeakingLibraryAttempt.weakPhonemesJson} for the library flow) is
 * already a flat JSON array of short IPA symbol strings (e.g. {@code ["θ", "ð"]}) -
 * ai-service's GOP scorer already reduced the mistake down to a crisp, LLM-friendly retry target,
 * so there is no "OPEN question is too diffuse a target" case here the way listening had, and no
 * topic-name fallback is needed: the phonemes themselves are always good enough generator input.
 * One shared parser covers both flows since Task 2 deliberately reused the exact same JSON shape.
 * No mapper/service dependency - a plain function over already-loaded JSON so it's unit-testable
 * without mocks.
 */
public final class SpeakingMistakeAnalyzer {

	private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

	private SpeakingMistakeAnalyzer() {
	}

	// Deserializes one attempt's persisted weakPhonemesJson back into a plain string list; a
	// missing/blank value (e.g. an attempt scored before this column existed, or one with no
	// mispronounced phonemes at all) returns an empty list rather than throwing.
	public static List<String> extractWeakPhonemes(String weakPhonemesJson) {
		if (weakPhonemesJson == null || weakPhonemesJson.isBlank()) {
			return List.of();
		}
		try {
			return OBJECT_MAPPER.readValue(weakPhonemesJson, new TypeReference<List<String>>() { });
		} catch (JsonProcessingException ex) {
			throw new IllegalStateException("Failed to deserialize speaking weak-phoneme input", ex);
		}
	}
}
