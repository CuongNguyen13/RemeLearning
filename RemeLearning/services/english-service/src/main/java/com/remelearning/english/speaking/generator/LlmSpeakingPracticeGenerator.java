package com.remelearning.english.speaking.generator;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.remelearning.english.learn.common.AiContentClient;
import com.remelearning.english.learn.common.AiContentException;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * The only {@link SpeakingPracticeGenerator}: this skill is AI-only, one Gemini call producing a
 * short sentence/passage that naturally reuses the learner's target words/sounds. Any LLM/parse
 * failure propagates as {@link AiContentException} (same as
 * {@code LlmSpeakingLibraryGenerator}) - deliberately no static-template fallback, since a
 * templated word list is not usable practice content for TTS/GOP scoring; the caller should see
 * the failure and retry.
 */
@Slf4j
@Component
public class LlmSpeakingPracticeGenerator implements SpeakingPracticeGenerator {

	private static final String SYSTEM_PROMPT = """
			You are an English-pronunciation coach building one short practice sentence/passage (1-3
			sentences, 8-25 words total) for a learner to read aloud. You're given a list of target
			words/sounds to naturally reuse (possibly empty - if empty, pick a suitable topic yourself
			for the requested level) plus an optional CEFR level and exam style. Respond with STRICTLY a
			raw JSON object (no markdown fences, no commentary) of the shape:
			{"topic": "...", "targetText": "...", "translation": "..."}
			- "targetText": natural, easy to read aloud, in English.
			- Every target word must appear INSIDE a meaningful, grammatical sentence. Never output a
			  comma-separated list of the target words and never output a meta-instruction such as
			  "Please practice saying this sentence clearly: ...".
			- Function words (a, of, and, the, ...) among the targets just have to occur naturally in
			  the sentence; do not force them into a list.
			- "translation": the Vietnamese translation of targetText.""";

	private final AiContentClient aiContentClient;
	private final int maxOutputTokens;

	public LlmSpeakingPracticeGenerator(
			AiContentClient aiContentClient,
			@Value("${speaking.practice.max-output-tokens:400}") int maxOutputTokens) {
		this.aiContentClient = aiContentClient;
		this.maxOutputTokens = maxOutputTokens;
	}

	// One LLM call, no fallback: an unusable/failed generation surfaces as AiContentException rather
	// than a templated word list, so the learner never gets handed fake practice content.
	@Override
	public GeneratedSpeakingPractice generate(List<String> targetWords, String level, String examType) {
		String userPrompt = "Target words/sounds: %s\nLevel: %s\nExam style: %s".formatted(
				targetWords.isEmpty() ? "(none - please choose a suitable topic yourself)" : targetWords,
				level == null ? "(unspecified)" : level,
				examType == null ? "(unspecified)" : examType);
		LlmPayload payload = aiContentClient.completeJson(SYSTEM_PROMPT, userPrompt, 0.6, maxOutputTokens, LlmPayload.class);
		if (payload.targetText == null || payload.targetText.isBlank()) {
			log.warn("LLM returned an empty speaking target sentence for words={} level={}", targetWords, level);
			throw new AiContentException("LLM returned an empty target sentence");
		}
		return new GeneratedSpeakingPractice(payload.topic, payload.targetText, payload.translation);
	}

	@Getter
	@Setter
	@JsonIgnoreProperties(ignoreUnknown = true)
	private static class LlmPayload {
		private String topic;
		private String targetText;
		private String translation;
	}
}
