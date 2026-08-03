package com.remelearning.english.dictation.analyzer;

import com.remelearning.common.ai.LlmClient;
import com.remelearning.common.ai.LlmResponse;
import com.remelearning.english.learn.common.AiContentException;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClientException;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class LlmDictationSentenceTranslatorTest {

	private final LlmClient llmClient = mock(LlmClient.class);
	private final LlmDictationSentenceTranslator translator = new LlmDictationSentenceTranslator(llmClient, 600);

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
	void throwsRatherThanReturningNullsWhenLlmCallFails() {
		when(llmClient.complete(any())).thenThrow(new RestClientException("ai-service unreachable"));

		assertThatThrownBy(() -> translator.translate(List.of("Hello.", "Bye."), "vi"))
				.isInstanceOf(AiContentException.class);
	}

	@Test
	void throwsWhenLlmReturnsMismatchedCount() {
		when(llmClient.complete(any())).thenReturn(LlmResponse.builder().content("[\"Xin chào.\"]").build());

		assertThatThrownBy(() -> translator.translate(List.of("Hello.", "Bye."), "vi"))
				.isInstanceOf(AiContentException.class);
	}

	@Test
	void returnsEmptyListForEmptyInput() {
		List<String> translations = translator.translate(List.of(), "vi");

		assertThat(translations).isEmpty();
	}
}
