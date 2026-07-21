package com.remelearning.english.dictation.analyzer;

import com.remelearning.common.ai.LlmClient;
import com.remelearning.common.ai.LlmResponse;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClientException;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class LlmDictationSentenceTranslatorTest {

	private final LlmClient llmClient = mock(LlmClient.class);
	private final LlmDictationSentenceTranslator translator = new LlmDictationSentenceTranslator(llmClient);

	@Test
	void translatesEachSentenceInOrder() {
		when(llmClient.complete(any())).thenReturn(LlmResponse.builder()
				.content("[\"Xin chào.\", \"Bạn khỏe không?\"]")
				.build());

		List<String> translations = translator.translate(List.of("Hello.", "How are you?"), "vi");

		assertThat(translations).containsExactly("Xin chào.", "Bạn khỏe không?");
	}

	@Test
	void stripsMarkdownCodeFencesBeforeParsing() {
		when(llmClient.complete(any())).thenReturn(LlmResponse.builder()
				.content("```json\n[\"Xin chào.\"]\n```")
				.build());

		List<String> translations = translator.translate(List.of("Hello."), "vi");

		assertThat(translations).containsExactly("Xin chào.");
	}

	@Test
	void returnsAllNullsSameSizeWhenLlmCallFails() {
		when(llmClient.complete(any())).thenThrow(new RestClientException("ai-service unreachable"));

		List<String> translations = translator.translate(List.of("Hello.", "Bye."), "vi");

		assertThat(translations).containsExactly(new String[] { null, null });
	}

	@Test
	void returnsAllNullsSameSizeWhenLlmReturnsMismatchedCount() {
		when(llmClient.complete(any())).thenReturn(LlmResponse.builder().content("[\"Xin chào.\"]").build());

		List<String> translations = translator.translate(List.of("Hello.", "Bye."), "vi");

		assertThat(translations).containsExactly(new String[] { null, null });
	}

	@Test
	void returnsEmptyListForEmptyInput() {
		List<String> translations = translator.translate(List.of(), "vi");

		assertThat(translations).isEmpty();
	}
}
