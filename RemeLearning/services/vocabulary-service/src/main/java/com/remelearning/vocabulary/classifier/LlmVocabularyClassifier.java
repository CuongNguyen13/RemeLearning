package com.remelearning.vocabulary.classifier;

import com.remelearning.common.ai.LlmClient;
import com.remelearning.common.ai.LlmRequest;
import com.remelearning.common.ai.LlmResponse;
import com.remelearning.vocabulary.domain.VocabularyType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;

/**
 * LLM-backed {@link VocabularyClassifier}, using whichever {@link LlmClient} is configured
 * (Gemini today, see common's {@code ai.gemini.GeminiLlmClientConfig}; other providers can
 * register their own {@link LlmClient} bean later without this class changing).
 * Disabled by default - enable with {@code vocabulary.classifier.mode=llm}, otherwise
 * {@link RuleBasedVocabularyClassifier} stays active.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "vocabulary.classifier", name = "mode", havingValue = "llm")
public class LlmVocabularyClassifier implements VocabularyClassifier {

	private static final String SYSTEM_PROMPT = """
			You are an English vocabulary classifier. Given a vocabulary label, reply with exactly one word
			from this list, uppercase, no punctuation, no explanation:
			NOUN, VERB, ADJECTIVE, ADVERB, PHRASAL_VERB, COLLOCATION, IDIOM, OTHER""";

	private final LlmClient llmClient;

	// Asks the LLM to classify the label into one of the VocabularyType names, at temperature 0
	// for a deterministic single-word answer; any parse failure or call error falls back to OTHER
	// rather than propagating, so a flaky LLM call can't break weak-point ingestion.
	@Override
	public VocabularyType classify(String label) {
		LlmRequest request = LlmRequest.builder()
				.systemPrompt(SYSTEM_PROMPT)
				.userPrompt(label)
				.temperature(0.0)
				.maxOutputTokens(10)
				.build();

		try {
			LlmResponse response = llmClient.complete(request);
			return VocabularyType.valueOf(response.getContent().trim().toUpperCase());
		} catch (IllegalArgumentException | IllegalStateException | RestClientException ex) {
			log.warn("LLM vocabulary classification failed for label '{}', defaulting to OTHER", label, ex);
			return VocabularyType.OTHER;
		}
	}
}
