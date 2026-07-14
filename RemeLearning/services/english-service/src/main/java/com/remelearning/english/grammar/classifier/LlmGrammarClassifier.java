package com.remelearning.english.grammar.classifier;

import com.remelearning.common.ai.LlmClient;
import com.remelearning.common.ai.LlmRequest;
import com.remelearning.common.ai.LlmResponse;
import com.remelearning.english.grammar.domain.GrammarType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;

/**
 * LLM-backed {@link GrammarClassifier}, using whichever {@link LlmClient} is configured
 * (Gemini today, see common's {@code ai.gemini.GeminiLlmClientConfig}). Disabled by default -
 * enable with {@code grammar.classifier.mode=llm}, otherwise {@link RuleBasedGrammarClassifier}
 * stays active.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "grammar.classifier", name = "mode", havingValue = "llm")
public class LlmGrammarClassifier implements GrammarClassifier {

	private static final String SYSTEM_PROMPT = """
			You are an English grammar-mistake classifier. Given a grammar weak-point label, reply with
			exactly one word from this list, uppercase, no punctuation, no explanation:
			TENSE, SUBJECT_VERB_AGREEMENT, ARTICLE, PREPOSITION, WORD_ORDER, PLURAL, PUNCTUATION, OTHER""";

	private final LlmClient llmClient;

	// Asks the LLM to classify the label into one of the GrammarType names, at temperature 0
	// for a deterministic single-word answer; any parse failure or call error falls back to OTHER
	// rather than propagating, so a flaky LLM call can't break weak-point ingestion.
	@Override
	public GrammarType classify(String label) {
		LlmRequest request = LlmRequest.builder()
				.systemPrompt(SYSTEM_PROMPT)
				.userPrompt(label)
				.temperature(0.0)
				.maxOutputTokens(10)
				.build();

		try {
			LlmResponse response = llmClient.complete(request);
			return GrammarType.valueOf(response.getContent().trim().toUpperCase());
		} catch (IllegalArgumentException | IllegalStateException | RestClientException ex) {
			log.warn("LLM grammar classification failed for label '{}', defaulting to OTHER", label, ex);
			return GrammarType.OTHER;
		}
	}
}
