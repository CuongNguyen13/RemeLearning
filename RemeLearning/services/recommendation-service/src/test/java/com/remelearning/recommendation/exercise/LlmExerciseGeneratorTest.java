package com.remelearning.recommendation.exercise;

import java.util.List;

import com.remelearning.common.ai.LlmClient;
import com.remelearning.common.ai.LlmException;
import com.remelearning.common.ai.LlmRequest;
import com.remelearning.common.ai.LlmResponse;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClientException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class LlmExerciseGeneratorTest {

	private final LlmClient llmClient = mock(LlmClient.class);
	private final LlmExerciseGenerator generator = new LlmExerciseGenerator(llmClient, 400);

	@Test
	void parsesAValidJsonArrayResponse() {
		when(llmClient.complete(any(LlmRequest.class))).thenReturn(LlmResponse.builder()
				.content("[\"Viet 5 cau vi du\", \"Lam 10 bai tap\", \"Ghi am ban than\"]")
				.build());

		List<String> exercises = generator.generate("grammar", "past perfect tense", 3.0414);

		assertThat(exercises).containsExactly("Viet 5 cau vi du", "Lam 10 bai tap", "Ghi am ban than");
	}

	@Test
	void stripsMarkdownCodeFencesBeforeParsing() {
		when(llmClient.complete(any(LlmRequest.class))).thenReturn(LlmResponse.builder()
				.content("```json\n[\"exercise one\", \"exercise two\"]\n```")
				.build());

		List<String> exercises = generator.generate("vocabulary", "reluctant", 0.8);

		assertThat(exercises).containsExactly("exercise one", "exercise two");
	}

	@Test
	void throwsRatherThanFallingBackToTemplatesWhenLlmCallThrows() {
		when(llmClient.complete(any(LlmRequest.class))).thenThrow(new RestClientException("boom"));

		assertThatThrownBy(() -> generator.generate("grammar", "past perfect tense", 3.0414))
				.isInstanceOf(LlmException.class);
	}

	@Test
	void throwsWhenResponseIsNotValidJson() {
		when(llmClient.complete(any(LlmRequest.class))).thenReturn(LlmResponse.builder()
				.content("sorry, I can't help with that")
				.build());

		assertThatThrownBy(() -> generator.generate("pronunciation", "th sound", 1.2))
				.isInstanceOf(LlmException.class);
	}

	@Test
	void throwsWhenResponseIsAnEmptyArray() {
		when(llmClient.complete(any(LlmRequest.class))).thenReturn(LlmResponse.builder()
				.content("[]")
				.build());

		assertThatThrownBy(() -> generator.generate("vocabulary", "reluctant", 0.8))
				.isInstanceOf(LlmException.class);
	}
}
