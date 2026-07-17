package com.remelearning.english.dictation.analyzer;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.remelearning.common.ai.LlmClient;
import com.remelearning.common.ai.LlmRequest;
import com.remelearning.common.ai.LlmResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;

import java.util.ArrayList;
import java.util.List;

/**
 * LLM-backed {@link DictationAnalyzer} using whichever {@link LlmClient} is configured (Gemini
 * today). Enabled with {@code dictation.analyzer.mode=llm}; otherwise
 * {@link RuleBasedDictationAnalyzer} stays active. Every call falls back to the static templates on
 * any LLM/parse failure, honoring the interface's never-throw contract.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "dictation.analyzer", name = "mode", havingValue = "llm")
public class LlmDictationAnalyzer implements DictationAnalyzer {

	private static final String ANALYZE_SYSTEM_PROMPT = """
			You are an English-listening coach. A learner did a dictation (listen-and-type) exercise
			and got some words wrong. Given the reference text and the list of missed words, respond
			with STRICTLY a raw JSON object (no markdown fences, no commentary) of the shape:
			{"suggestions": ["...", "..."], "practiceSentences": ["...", "..."]}
			- "suggestions": 2-4 short, concrete tips in Vietnamese on how to hear these words better.
			- "practiceSentences": 3-5 short, natural English sentences (6-14 words) that each reuse
			  one or more of the missed words, suitable for a follow-up listen-and-type exercise.""";

	private static final String PRACTICE_SYSTEM_PROMPT = """
			You are an English-listening coach. Given a learner's recurring hard-to-hear words, write
			5 short, natural English sentences (6-14 words) that each reuse one or more of them, for a
			listen-and-type practice. Respond with STRICTLY a raw JSON array of strings, no markdown
			fences, no commentary.""";

	private static final ObjectMapper MAPPER = new ObjectMapper();

	private final LlmClient llmClient;

	// Asks the LLM for suggestions + practice sentences as one JSON object; any failure/empty parse
	// falls back to the static templates so grading an attempt never breaks.
	@Override
	public DictationAnalysis analyzeAttempt(String referenceText, List<String> missedWords) {
		LlmRequest request = LlmRequest.builder()
				.systemPrompt(ANALYZE_SYSTEM_PROMPT)
				.userPrompt("Reference text:\n%s\n\nMissed words: %s".formatted(referenceText, missedWords))
				.temperature(0.4)
				.maxOutputTokens(500)
				.build();
		try {
			LlmResponse response = llmClient.complete(request);
			JsonNode root = MAPPER.readTree(stripCodeFences(response.getContent()));
			List<String> suggestions = readStringArray(root.get("suggestions"));
			List<String> practice = readStringArray(root.get("practiceSentences"));
			if (suggestions.isEmpty() && practice.isEmpty()) {
				throw new IllegalStateException("LLM returned an empty analysis");
			}
			return DictationAnalysis.builder()
					.suggestions(suggestions.isEmpty() ? DictationAnalysisTemplates.suggestionsFor(missedWords) : suggestions)
					.practiceSentences(practice.isEmpty() ? DictationAnalysisTemplates.practiceSentencesFor(missedWords) : practice)
					.build();
		} catch (JsonProcessingException | IllegalStateException | RestClientException ex) {
			log.warn("LLM dictation analysis failed for {} missed words, falling back to templates",
					missedWords.size(), ex);
			return DictationAnalysis.builder()
					.suggestions(DictationAnalysisTemplates.suggestionsFor(missedWords))
					.practiceSentences(DictationAnalysisTemplates.practiceSentencesFor(missedWords))
					.build();
		}
	}

	// Asks the LLM for a JSON array of practice sentences; falls back to templates on any failure.
	@Override
	public List<String> generatePracticeSentences(List<String> missedWords) {
		LlmRequest request = LlmRequest.builder()
				.systemPrompt(PRACTICE_SYSTEM_PROMPT)
				.userPrompt("Hard-to-hear words: " + missedWords)
				.temperature(0.5)
				.maxOutputTokens(400)
				.build();
		try {
			LlmResponse response = llmClient.complete(request);
			List<String> sentences = readStringArray(MAPPER.readTree(stripCodeFences(response.getContent())));
			if (sentences.isEmpty()) {
				throw new IllegalStateException("LLM returned no practice sentences");
			}
			return sentences;
		} catch (JsonProcessingException | IllegalStateException | RestClientException ex) {
			log.warn("LLM practice-sentence generation failed, falling back to templates", ex);
			return DictationAnalysisTemplates.practiceSentencesFor(missedWords);
		}
	}

	// Reads a JSON array node into a list of non-blank strings; returns empty for a null/non-array node.
	private static List<String> readStringArray(JsonNode node) {
		List<String> values = new ArrayList<>();
		if (node != null && node.isArray()) {
			for (JsonNode element : node) {
				String text = element.asText("").trim();
				if (!text.isBlank()) {
					values.add(text);
				}
			}
		}
		return values;
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
