package com.remelearning.english.learn.common;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.remelearning.common.ai.LlmClient;
import com.remelearning.common.ai.LlmRequest;
import com.remelearning.common.ai.LlmResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;

/**
 * Thin wrapper around {@link LlmClient} shared by every "Học &amp; Luyện tập với AI" skill
 * generator/scorer (vocabulary/grammar/listening/speaking) - previously each generator (see
 * dictation's {@code LlmDictationAnalyzer}/{@code LlmDictationDialogueGenerator}) duplicated its
 * own code-fence stripping and JSON parsing. Callers still own their own fallback-on-failure
 * template, this only collapses the call+parse plumbing into one place and one exception type.
 */
@Component
@RequiredArgsConstructor
public class AiContentClient {

	private static final ObjectMapper MAPPER = new ObjectMapper();

	private final LlmClient llmClient;

	/** Raw text completion, code-fence-stripped; throws {@link AiContentException} on any call failure. */
	public String complete(String systemPrompt, String userPrompt, double temperature, int maxOutputTokens) {
		try {
			LlmResponse response = llmClient.complete(LlmRequest.builder()
					.systemPrompt(systemPrompt)
					.userPrompt(userPrompt)
					.temperature(temperature)
					.maxOutputTokens(maxOutputTokens)
					.build());
			return stripCodeFences(response.getContent());
		} catch (RestClientException ex) {
			throw new AiContentException("LLM call failed", ex);
		}
	}

	/** Completes and parses the response as JSON of the given type; throws {@link AiContentException} on any call/parse failure. */
	public <T> T completeJson(String systemPrompt, String userPrompt, double temperature, int maxOutputTokens, Class<T> type) {
		String content = complete(systemPrompt, userPrompt, temperature, maxOutputTokens);
		try {
			return MAPPER.readValue(content, type);
		} catch (JsonProcessingException ex) {
			throw new AiContentException("LLM response was not valid JSON", ex);
		}
	}

	/** Same as {@link #completeJson(String, String, double, int, Class)}, for generic types (e.g. {@code List<String>}). */
	public <T> T completeJson(String systemPrompt, String userPrompt, double temperature, int maxOutputTokens, TypeReference<T> type) {
		String content = complete(systemPrompt, userPrompt, temperature, maxOutputTokens);
		try {
			return MAPPER.readValue(content, type);
		} catch (JsonProcessingException ex) {
			throw new AiContentException("LLM response was not valid JSON", ex);
		}
	}

	// Gemini occasionally wraps JSON in a ```json ... ``` fence despite being asked not to; strip it.
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
