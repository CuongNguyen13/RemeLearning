package com.remelearning.english.dictation.analyzer;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.remelearning.common.ai.LlmClient;
import com.remelearning.common.ai.LlmRequest;
import com.remelearning.common.ai.LlmResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * {@link DictationSentenceTranslator} backed by whichever {@link LlmClient} is configured. One
 * batched call per clip (not one call per sentence) to keep this cheap; any failure or count
 * mismatch degrades to an all-null result the same size as the input, honoring the interface's
 * never-throw contract.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class LlmDictationSentenceTranslator implements DictationSentenceTranslator {

	private static final String SYSTEM_PROMPT = """
            You are a translation engine for an English-listening dictation app. Translate each of the
            given English sentences into %s, preserving order and count exactly - one output string per
            input sentence, in the same order.

            Respond with STRICTLY a raw JSON array of strings (no markdown fences, no commentary).""";

	private static final ObjectMapper MAPPER = new ObjectMapper();

	private final LlmClient llmClient;

	@Override
	public List<String> translate(List<String> sentences, String targetLang) {
		// Empty input shortcut - return empty list immediately.
		if (sentences == null || sentences.isEmpty()) {
			return List.of();
		}

		// Build and send batched LLM request with system prompt specifying output format.
		LlmRequest request = LlmRequest.builder()
				.systemPrompt(SYSTEM_PROMPT.formatted(languageName(targetLang)))
				.userPrompt(String.join("\n", sentences))
				.temperature(0.2)
				.maxOutputTokens(600)
				.build();

		try {
			// Call LLM and parse JSON array response, stripping any markdown code fences.
			LlmResponse response = llmClient.complete(request);
			List<String> translations = readStringArray(MAPPER.readTree(stripCodeFences(response.getContent())));

			// Validate response count matches input count; if mismatch, return all nulls.
			if (translations.size() != sentences.size()) {
				throw new IllegalStateException(
						"Translation count %d did not match sentence count %d".formatted(translations.size(), sentences.size()));
			}

			return translations;
		} catch (JsonProcessingException | IllegalStateException | RestClientException ex) {
			// Any failure degrades gracefully to all nulls, honoring the never-throw contract.
			log.warn("Sentence translation to {} failed for {} sentences, returning no translations", targetLang, sentences.size(), ex);
			return Collections.nCopies(sentences.size(), null);
		}
	}

	/**
	 * Maps language code to human-readable language name for the system prompt. Falls back to the
	 * code itself if unknown (e.g., "es" -> "es", but "vi" -> "Vietnamese" for better LLM clarity).
	 */
	private String languageName(String code) {
		return "vi".equalsIgnoreCase(code) ? "Vietnamese" : code;
	}

	/**
	 * Reads a JSON array of strings from a JsonNode, returning an empty list if the node is not
	 * itself an array.
	 */
	private List<String> readStringArray(JsonNode root) {
		List<String> values = new ArrayList<>();
		if (root.isArray()) {
			for (JsonNode node : root) {
				values.add(node.asText());
			}
		}
		return values;
	}

	/**
	 * Strips leading/trailing markdown code fences (```...```) from LLM response text, returning
	 * the content within or the original text if no fences are found.
	 */
	private static String stripCodeFences(String content) {
		String trimmed = content.trim();
		if (trimmed.startsWith("```")) {
			// Skip to first newline after opening fence.
			trimmed = trimmed.substring(trimmed.indexOf('\n') + 1);

			// Find and strip closing fence.
			int lastFence = trimmed.lastIndexOf("```");
			if (lastFence >= 0) {
				trimmed = trimmed.substring(0, lastFence);
			}
		}
		return trimmed.trim();
	}
}
