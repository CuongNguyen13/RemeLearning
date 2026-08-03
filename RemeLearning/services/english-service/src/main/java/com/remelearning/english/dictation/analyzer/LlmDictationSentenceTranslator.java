package com.remelearning.english.dictation.analyzer;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.remelearning.common.ai.LlmClient;
import com.remelearning.common.ai.LlmException;
import com.remelearning.common.ai.LlmRequest;
import com.remelearning.common.ai.LlmResponse;
import com.remelearning.english.learn.common.AiContentException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;

import java.util.ArrayList;
import java.util.List;

/**
 * {@link DictationSentenceTranslator} backed by whichever {@link LlmClient} is configured. One
 * batched call per clip (not one call per sentence) to keep this cheap; any failure or count
 * mismatch propagates as {@link AiContentException} rather than degrading to an all-null result.
 */
@Slf4j
@Component
public class LlmDictationSentenceTranslator implements DictationSentenceTranslator {

	private static final String SYSTEM_PROMPT = """
            You are a translation engine for an English-listening dictation app. Translate each of the
            given English sentences into %s, preserving order and count exactly - one output string per
            input sentence, in the same order.

            Respond with STRICTLY a raw JSON array of strings (no markdown fences, no commentary).""";

	private static final ObjectMapper MAPPER = new ObjectMapper();

	private final LlmClient llmClient;
	private final int maxOutputTokens;

	public LlmDictationSentenceTranslator(
			LlmClient llmClient,
			@Value("${dictation.translator.max-output-tokens:600}") int maxOutputTokens) {
		this.llmClient = llmClient;
		this.maxOutputTokens = maxOutputTokens;
	}

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
				.maxOutputTokens(maxOutputTokens)
				.build();

		try {
			// Call LLM and parse JSON array response, stripping any markdown code fences.
			LlmResponse response = llmClient.complete(request);
			List<String> translations = readStringArray(MAPPER.readTree(stripCodeFences(response.getContent())));

			// A count mismatch means the translations can no longer be paired with their sentences -
			// reported as an error rather than degraded to nulls.
			if (translations.size() != sentences.size()) {
				throw new AiContentException(
						"Translation count %d did not match sentence count %d".formatted(translations.size(), sentences.size()));
			}

			return translations;
		} catch (JsonProcessingException | LlmException | RestClientException ex) {
			log.warn("Sentence translation to {} failed for {} sentences", targetLang, sentences.size(), ex);
			throw new AiContentException("Sentence translation to %s failed".formatted(targetLang), ex);
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
