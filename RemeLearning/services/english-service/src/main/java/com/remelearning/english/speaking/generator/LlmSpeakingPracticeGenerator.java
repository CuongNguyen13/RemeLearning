package com.remelearning.english.speaking.generator;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.remelearning.english.learn.common.AiContentClient;
import com.remelearning.english.learn.common.AiContentException;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * The only {@link SpeakingPracticeGenerator}: this skill is AI-only, one Gemini call producing a
 * short sentence/passage that naturally reuses the learner's target words/sounds - a
 * static-template fallback covers any LLM/parse failure so generating a passage never breaks.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class LlmSpeakingPracticeGenerator implements SpeakingPracticeGenerator {

	private static final String SYSTEM_PROMPT = """
			You are an English-pronunciation coach building one short practice sentence/passage (1-3
			sentences, 8-25 words total) for a learner to read aloud. You're given a list of target
			words/sounds to naturally reuse (possibly empty - if empty, pick a suitable topic yourself
			for the requested level) plus an optional CEFR level and exam style. Respond with STRICTLY a
			raw JSON object (no markdown fences, no commentary) of the shape:
			{"topic": "...", "targetText": "...", "translation": "..."}
			- "targetText": natural, easy to read aloud, in English.
			- "translation": the Vietnamese translation of targetText.""";

	private final AiContentClient aiContentClient;

	@Override
	public GeneratedSpeakingPractice generate(List<String> targetWords, String level, String examType) {
		try {
			String userPrompt = "Target words/sounds: %s\nLevel: %s\nExam style: %s".formatted(
					targetWords.isEmpty() ? "(none - please choose a suitable topic yourself)" : targetWords,
					level == null ? "(unspecified)" : level,
					examType == null ? "(unspecified)" : examType);
			LlmPayload payload = aiContentClient.completeJson(SYSTEM_PROMPT, userPrompt, 0.6, 400, LlmPayload.class);
			if (payload.targetText == null || payload.targetText.isBlank()) {
				throw new AiContentException("LLM returned an empty target sentence");
			}
			return new GeneratedSpeakingPractice(payload.topic, payload.targetText, payload.translation);
		} catch (AiContentException ex) {
			log.warn("LLM speaking practice generation failed, falling back to a template", ex);
			return fallback(targetWords, level);
		}
	}

	private GeneratedSpeakingPractice fallback(List<String> targetWords, String level) {
		String targetText = targetWords.isEmpty()
				? "The weather today is quite pleasant for a walk in the park."
				: "Please practice saying this sentence clearly: " + String.join(", ", targetWords) + ".";
		return new GeneratedSpeakingPractice(level == null ? "Speaking practice" : level + " speaking practice", targetText, null);
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
