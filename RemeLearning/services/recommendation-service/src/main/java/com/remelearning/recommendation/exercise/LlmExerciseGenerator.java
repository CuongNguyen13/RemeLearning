package com.remelearning.recommendation.exercise;

import java.util.List;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.remelearning.common.ai.LlmClient;
import com.remelearning.common.ai.LlmRequest;
import com.remelearning.common.ai.LlmResponse;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;

/**
 * LLM-backed {@link ExerciseGenerator}, using whichever {@link LlmClient} is configured (Gemini
 * today, see common's {@code ai.gemini.GeminiLlmClientConfig}). One category-agnostic prompt
 * serves vocabulary, grammar, pronunciation, and any future category alike - unlike
 * english-service's per-domain {@code Llm*Classifier}s, this single component handles every
 * category since recommendation-service never filters by category.
 * Disabled by default - enable with {@code recommendation.exercise-generator.mode=llm}, otherwise
 * {@link RuleBasedExerciseGenerator} stays active.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "recommendation.exercise-generator", name = "mode", havingValue = "llm")
public class LlmExerciseGenerator implements ExerciseGenerator {

	private static final String SYSTEM_PROMPT = """
			You are an English-learning coach. Given a learner's weak point - a skill category and a
			specific item they struggle with - suggest 3 to 5 concrete, actionable practice exercises
			they can do today to improve. Write the exercises in Vietnamese.
			Respond with STRICTLY a raw JSON array of strings, one exercise per element, e.g.
			["exercise one", "exercise two", "exercise three"]. No markdown code fences, no
			commentary before or after the array.""";

	private static final ObjectMapper MAPPER = new ObjectMapper();
	private static final TypeReference<List<String>> STRING_LIST = new TypeReference<>() {
	};

	private final LlmClient llmClient;

	// Asks the LLM for 3-5 concrete exercises as a JSON array; any call failure or malformed/empty
	// response falls back to the static per-category templates rather than propagating, so a flaky
	// LLM call can't break weak-point ingestion.
	@Override
	public List<String> generate(String category, String label, double forgettingScore) {
		LlmRequest request = LlmRequest.builder()
				.systemPrompt(SYSTEM_PROMPT)
				.userPrompt("Category: %s\nWeak point: %s\nForgetting score: %.4f (higher = more urgent)"
						.formatted(category, label, forgettingScore))
				.temperature(0.4)
				.maxOutputTokens(400)
				.build();

		try {
			LlmResponse response = llmClient.complete(request);
			List<String> exercises = MAPPER.readValue(stripCodeFences(response.getContent()), STRING_LIST);
			if (exercises == null || exercises.isEmpty()) {
				throw new IllegalStateException("LLM returned an empty exercise list");
			}
			return exercises;
		} catch (JsonProcessingException | IllegalStateException | RestClientException ex) {
			log.warn("LLM exercise generation failed for category '{}', label '{}', falling back to templates",
					category, label, ex);
			return ExerciseTemplates.defaultsFor(category, label);
		}
	}

	// Gemini occasionally wraps JSON in a ```json ... ``` fence despite being asked not to; strip
	// it so the response still parses as a plain JSON array.
	private static String stripCodeFences(String content) {
		String trimmed = content.trim();
		if (trimmed.startsWith("```")) {
			trimmed = trimmed.substring(trimmed.indexOf('\n') + 1);
			int lastFence = trimmed.lastIndexOf("```");
			if (lastFence >= 0) {
				trimmed = trimmed.substring(0, lastFence);
			}
		}
		return trimmed.trim();
	}
}
