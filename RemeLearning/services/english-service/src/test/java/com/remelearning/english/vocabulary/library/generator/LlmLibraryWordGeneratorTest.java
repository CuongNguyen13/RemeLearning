package com.remelearning.english.vocabulary.library.generator;

import com.remelearning.english.learn.common.AiContentClient;
import com.remelearning.english.learn.common.AiContentException;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class LlmLibraryWordGeneratorTest {

	private final AiContentClient aiContentClient = mock(AiContentClient.class);
	private final LlmLibraryWordGenerator generator = new LlmLibraryWordGenerator(aiContentClient);

	@Test
	void generateParsesLlmWordsIntoGeneratedLibraryWords() {
		String json = """
				{"words": [
				  {"word": "itinerary", "wordType": "NOUN", "ipa": "aɪˈtɪnərəri", "meaningVi": "lịch trình", "exampleEn": "She planned a detailed itinerary for the trip."}
				]}""";
		when(aiContentClient.completeJson(anyString(), anyString(), anyDouble(), anyInt(), eq(LlmLibraryWordGenerator.LlmPayload.class)))
				.thenAnswer(invocation -> new com.fasterxml.jackson.databind.ObjectMapper().readValue(json, LlmLibraryWordGenerator.LlmPayload.class));

		List<GeneratedLibraryWord> result = generator.generate("Travel", List.of("passport"), 1);

		assertThat(result).hasSize(1);
		assertThat(result.get(0).word()).isEqualTo("itinerary");
		assertThat(result.get(0).wordType()).isEqualTo("NOUN");
		assertThat(result.get(0).ipa()).isEqualTo("aɪˈtɪnərəri");
		assertThat(result.get(0).meaningVi()).isEqualTo("lịch trình");
		assertThat(result.get(0).exampleEn()).contains("itinerary");
	}

	@Test
	void generateReturnsEmptyListRatherThanThrowingWhenTheLlmCallFails() {
		when(aiContentClient.completeJson(anyString(), anyString(), anyDouble(), anyInt(), any(Class.class)))
				.thenThrow(new AiContentException("LLM call failed", new RuntimeException("boom")));

		List<GeneratedLibraryWord> result = generator.generate("Travel", List.of(), 5);

		assertThat(result).isEmpty();
	}
}
