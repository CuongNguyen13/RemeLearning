package com.remelearning.english.speaking.generator;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.remelearning.english.learn.common.AiContentClient;
import com.remelearning.english.learn.common.AiContentException;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class LlmSpeakingPracticeGeneratorTest {

	private final AiContentClient aiContentClient = mock(AiContentClient.class);
	private final LlmSpeakingPracticeGenerator generator = new LlmSpeakingPracticeGenerator(aiContentClient, 400);

	@Test
	void returnsTheGeneratedSentenceWithItsTopicAndTranslation() {
		stubLlm("""
				{"topic": "Morning routine", "targetText": "I usually wake up early and drink a cup of tea.",
				 "translation": "Tôi thường dậy sớm và uống một tách trà."}""");

		GeneratedSpeakingPractice practice = generator.generate(List.of("wake", "early", "drink"), "A2", null);

		assertThat(practice.topic()).isEqualTo("Morning routine");
		assertThat(practice.targetText()).isEqualTo("I usually wake up early and drink a cup of tea.");
		assertThat(practice.translation()).isEqualTo("Tôi thường dậy sớm và uống một tách trà.");
	}

	// The generator used to emit a meta-instruction plus a raw word list ("Please practice saying
	// this sentence clearly: I, drink, ...") whenever the LLM failed; that template is gone.
	@Test
	void throwsRatherThanFallingBackToATemplateWhenTheLlmFails() {
		when(aiContentClient.completeJson(anyString(), anyString(), anyDouble(), anyInt(), any(Class.class)))
				.thenThrow(new AiContentException("LLM call failed", new RuntimeException("boom")));

		assertThatThrownBy(() -> generator.generate(
				List.of("I", "drink", "usually", "and", "a", "of", "wake", "early"), "A2", null))
				.isInstanceOf(AiContentException.class);
	}

	@Test
	void throwsWhenTheLlmReturnsAnEmptyTargetSentence() {
		stubLlm("{\"topic\": \"Daily routine\", \"targetText\": \"  \"}");

		assertThatThrownBy(() -> generator.generate(List.of("wake"), "A2", null))
				.isInstanceOf(AiContentException.class);
	}

	// The payload type is private to the generator, so the stub deserializes into whichever class the
	// generator itself asked for (argument 4 of completeJson).
	private void stubLlm(String json) {
		when(aiContentClient.completeJson(anyString(), anyString(), anyDouble(), anyInt(), any(Class.class)))
				.thenAnswer(invocation -> new ObjectMapper().readValue(json, invocation.getArgument(4, Class.class)));
	}
}
