package com.remelearning.english.writing.generator;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.remelearning.english.writing.domain.WritingErrorItem;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Pure helper that pulls the weak-point labels out of a past attempt's persisted {@code errorsJson},
 * so the "Luyện lại những lỗi này" action can target a fresh prompt at exactly what the learner got
 * wrong. Kept as a plain static utility (like {@code ListeningMistakeAnalyzer}) rather than a Spring
 * bean so it stays testable without an LLM or a database.
 */
public final class WritingMistakeAnalyzer {

	private static final ObjectMapper MAPPER = new ObjectMapper();

	private WritingMistakeAnalyzer() {
	}

	/**
	 * Distinct error labels from one attempt, in the order they were reported. Never throws: an
	 * empty/absent/malformed {@code errorsJson} yields an empty list, which the generator treats as
	 * "pick your own topic" - the same as a brand-new learner with no history.
	 */
	public static List<String> extractMistakeLabels(String errorsJson) {
		if (errorsJson == null || errorsJson.isBlank()) {
			return List.of();
		}
		List<WritingErrorItem> errors;
		try {
			errors = MAPPER.readValue(errorsJson, new TypeReference<List<WritingErrorItem>>() { });
		} catch (Exception ex) {
			return List.of();
		}
		Set<String> labels = new LinkedHashSet<>();
		for (WritingErrorItem error : errors) {
			if (error.getLabel() != null && !error.getLabel().isBlank()) {
				labels.add(error.getLabel().trim());
			}
		}
		return List.copyOf(labels);
	}
}
