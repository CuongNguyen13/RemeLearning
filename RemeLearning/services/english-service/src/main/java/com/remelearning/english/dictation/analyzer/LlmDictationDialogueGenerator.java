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
 * {@link DictationDialogueGenerator} backed by whichever {@link LlmClient} is configured (Gemini
 * today) - always active, unlike {@link DictationAnalyzer}'s rule-based/llm toggle, since a
 * templated one-word-per-line fallback would defeat the point of this feature. A failed LLM call or
 * unparsable response therefore propagates as {@link AiContentException} instead of producing that
 * template.
 */
@Slf4j
@Component
public class LlmDictationDialogueGenerator implements DictationDialogueGenerator {

	private static final String BASE_INSTRUCTIONS = """
            You are an English listening-practice content generator for a dictation app. Given a list of words/phrases a learner has trouble hearing, write a short dialogue between 2-3 named speakers that naturally reuses ALL of the provided words/phrases.

            Strict Constraints:

            Structure: It MUST be a dialogue containing AT LEAST 10 sentences in total.

            Difficulty: The vocabulary and grammar must align with the CEFR %s level.
            %s
            Thematic Unity: The conversation MUST strictly revolve around ONE single, coherent topic or scenario - name that topic/scenario concisely.

            Length: Keep the total word count concise (around 120-150 words) while fulfilling the 10-sentence minimum.
            %s
            Respond with STRICTLY a raw JSON object (no markdown fences, no code blocks, no commentary) of the shape {"topic": "short topic label", "lines": [{"speaker": "SpeakerName", "text": "..."%s}, ...]}. Use a distinct name per person, allocating one array element per conversational turn or line.""";

	private static final ObjectMapper MAPPER = new ObjectMapper();
	private static final String DEFAULT_SPEAKER = "Narrator";
	private static final String DEFAULT_LEVEL = "B2";

	private final LlmClient llmClient;
	private final int maxOutputTokens;
	private final int maxOutputTokensWithTranslation;

	public LlmDictationDialogueGenerator(
			LlmClient llmClient,
			@Value("${dictation.dialogue.max-output-tokens:400}") int maxOutputTokens,
			@Value("${dictation.dialogue.max-output-tokens-with-translation:700}") int maxOutputTokensWithTranslation) {
		this.llmClient = llmClient;
		this.maxOutputTokens = maxOutputTokens;
		this.maxOutputTokensWithTranslation = maxOutputTokensWithTranslation;
	}

	// Asks the LLM for the passage as a JSON object {topic, lines: [{speaker, text, translation?}]};
	// any failure or empty parse propagates as AiContentException - no templated substitute.
	@Override
	public DialogueGenerationResult generateDialogue(
			List<String> targetPhrases, String level, String examType, String translationLang) {
		List<String> phrases = targetPhrases == null ? List.of() : targetPhrases;
		boolean wantsTranslation = translationLang != null && !"en".equalsIgnoreCase(translationLang);
		LlmRequest request = LlmRequest.builder()
				.systemPrompt(buildSystemPrompt(level, examType, wantsTranslation, translationLang))
				.userPrompt("Words/phrases to practice: "
						+ (phrases.isEmpty() ? "(none - pick any everyday topic)" : String.join(", ", phrases)))
				.temperature(0.7)
				.maxOutputTokens(wantsTranslation ? maxOutputTokensWithTranslation : maxOutputTokens)
				.build();
		try {
			LlmResponse response = llmClient.complete(request);
			DialogueGenerationResult result = readDialogueObject(MAPPER.readTree(stripCodeFences(response.getContent())));
			if (result.lines().isEmpty()) {
				throw new AiContentException("LLM returned no dialogue lines");
			}
			return result;
		} catch (JsonProcessingException | LlmException | RestClientException ex) {
			log.warn("LLM dialogue generation failed for {} target phrases", phrases.size(), ex);
			throw new AiContentException("LLM dialogue generation failed", ex);
		}
	}

	// Composes the constraint block: CEFR level (defaults to B2 when unspecified), an optional
	// exam-style framing line, and an optional translation instruction/schema field.
	private String buildSystemPrompt(String level, String examType, boolean wantsTranslation, String translationLang) {
		String examLine = examType == null || examType.isBlank()
				? ""
				: "\nExam framing: Write the dialogue in a style suited to preparing for the " + examType + " exam.\n";
		String translationLine = wantsTranslation
				? "\nTranslation: Also translate every line into " + languageName(translationLang) + ".\n"
				: "";
		String translationField = wantsTranslation ? ", \"translation\": \"...\"" : "";
		return BASE_INSTRUCTIONS.formatted(
				level == null || level.isBlank() ? DEFAULT_LEVEL : level, examLine, translationLine, translationField);
	}

	private String languageName(String code) {
		return "vi".equalsIgnoreCase(code) ? "Vietnamese" : code;
	}

	// Reads {"topic": "...", "lines": [{speaker, text, translation?}]} into a result, skipping any
	// line with blank text.
	private DialogueGenerationResult readDialogueObject(JsonNode root) {
		String topic = root.path("topic").asText("").trim();
		List<DictationDialogueLine> lines = new ArrayList<>();
		for (JsonNode node : root.path("lines")) {
			String text = node.path("text").asText("").trim();
			if (!text.isBlank()) {
				String speaker = node.path("speaker").asText(DEFAULT_SPEAKER).trim();
				String translation = node.path("translation").asText("").trim();
				lines.add(new DictationDialogueLine(
						speaker.isBlank() ? DEFAULT_SPEAKER : speaker, text, translation.isBlank() ? null : translation));
			}
		}
		return new DialogueGenerationResult(topic.isBlank() ? null : topic, lines);
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
