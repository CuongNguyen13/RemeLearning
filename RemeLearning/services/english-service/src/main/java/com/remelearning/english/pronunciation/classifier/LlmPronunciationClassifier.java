package com.remelearning.english.pronunciation.classifier;

import com.remelearning.common.ai.LlmClient;
import com.remelearning.common.ai.LlmException;
import com.remelearning.common.ai.LlmRequest;
import com.remelearning.common.ai.LlmResponse;
import com.remelearning.english.pronunciation.domain.PronunciationType;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;

/**
 * LLM-backed {@link PronunciationClassifier}, using whichever {@link LlmClient} is configured
 * (Gemini today, see common's {@code ai.gemini.GeminiLlmClientConfig}). Disabled by default -
 * enable with {@code pronunciation.classifier.mode=llm}, otherwise
 * {@link RuleBasedPronunciationClassifier} stays active.
 */
@Slf4j
@Component
@ConditionalOnProperty(prefix = "pronunciation.classifier", name = "mode", havingValue = "llm")
public class LlmPronunciationClassifier implements PronunciationClassifier {

	private static final String SYSTEM_PROMPT = """
			You are an English pronunciation-mistake classifier. Given a pronunciation weak-point label,
			reply with exactly one word from this list, uppercase, no punctuation, no explanation:
			VOWEL, CONSONANT, STRESS, INTONATION, LINKING, RHYTHM, OTHER""";

	private final LlmClient llmClient;
	private final int maxOutputTokens;

	public LlmPronunciationClassifier(
			LlmClient llmClient,
			@Value("${pronunciation.classifier.max-output-tokens:10}") int maxOutputTokens) {
		this.llmClient = llmClient;
		this.maxOutputTokens = maxOutputTokens;
	}

	// Asks the LLM to classify the label into one of the PronunciationType names, at temperature 0
	// for a deterministic single-word answer; a parse failure or call error propagates as
	// LlmException instead of being silently recorded as OTHER.
	@Override
	public PronunciationType classify(String label) {
		LlmRequest request = LlmRequest.builder()
				.systemPrompt(SYSTEM_PROMPT)
				.userPrompt(label)
				.temperature(0.0)
				.maxOutputTokens(maxOutputTokens)
				.build();

		try {
			LlmResponse response = llmClient.complete(request);
			return PronunciationType.valueOf(response.getContent().trim().toUpperCase());
		} catch (IllegalArgumentException | RestClientException ex) {
			log.warn("LLM pronunciation classification failed for label '{}'", label, ex);
			throw new LlmException("LLM pronunciation classification failed for label " + label, ex);
		}
	}
}
