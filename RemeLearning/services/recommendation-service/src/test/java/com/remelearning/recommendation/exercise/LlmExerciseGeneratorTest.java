package com.remelearning.recommendation.exercise;

import java.util.List;

import com.remelearning.common.ai.LlmClient;
import com.remelearning.common.ai.LlmRequest;
import com.remelearning.common.ai.LlmResponse;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClientException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class LlmExerciseGeneratorTest {

	private final LlmClient llmClient = mock(LlmClient.class);
	private final LlmExerciseGenerator generator = new LlmExerciseGenerator(llmClient);

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
	void fallsBackToTemplatesWhenLlmCallThrows() {
		when(llmClient.complete(any(LlmRequest.class))).thenThrow(new RestClientException("boom"));

		List<String> exercises = generator.generate("grammar", "past perfect tense", 3.0414);

		assertThat(exercises).isEqualTo(ExerciseTemplates.defaultsFor("grammar", "past perfect tense"));
	}

	@Test
	void fallsBackToTemplatesWhenResponseIsNotValidJson() {
		when(llmClient.complete(any(LlmRequest.class))).thenReturn(LlmResponse.builder()
				.content("sorry, I can't help with that")
				.build());

		List<String> exercises = generator.generate("pronunciation", "th sound", 1.2);

		assertThat(exercises).isEqualTo(ExerciseTemplates.defaultsFor("pronunciation", "th sound"));
	}

	@Test
	void fallsBackToTemplatesWhenResponseIsAnEmptyArray() {
		when(llmClient.complete(any(LlmRequest.class))).thenReturn(LlmResponse.builder()
				.content("[]")
				.build());

		List<String> exercises = generator.generate("vocabulary", "reluctant", 0.8);

		assertThat(exercises).isEqualTo(ExerciseTemplates.defaultsFor("vocabulary", "reluctant"));
	}
}
