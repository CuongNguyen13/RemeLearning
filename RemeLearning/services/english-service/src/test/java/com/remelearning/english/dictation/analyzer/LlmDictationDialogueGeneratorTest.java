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

class LlmDictationDialogueGeneratorTest {

	private final LlmClient llmClient = mock(LlmClient.class);
	private final LlmDictationDialogueGenerator generator = new LlmDictationDialogueGenerator(llmClient);

	@Test
	void parsesTopicAndSpeakerTaggedLinesFromJsonObject() {
		when(llmClient.complete(any())).thenReturn(LlmResponse.builder()
				.content("""
						{"topic": "Returning a faulty product", "lines": [
						  {"speaker": "Alex", "text": "Did you see that reluctant look?"},
						  {"speaker": "Sam", "text": "Yes, he seemed hesitant."}
						]}""")
				.build());

		DialogueGenerationResult result = generator.generateDialogue(List.of("reluctant", "hesitant"), null, null, null);

		assertThat(result.topic()).isEqualTo("Returning a faulty product");
		assertThat(result.lines()).containsExactly(
				new DictationDialogueLine("Alex", "Did you see that reluctant look?", null),
				new DictationDialogueLine("Sam", "Yes, he seemed hesitant.", null));
	}

	@Test
	void parsesPerLineTranslationWhenTranslationLangRequested() {
		when(llmClient.complete(any())).thenReturn(LlmResponse.builder()
				.content("""
						{"topic": "Ordering coffee", "lines": [
						  {"speaker": "Narrator", "text": "Listen carefully.", "translation": "Hãy lắng nghe cẩn thận."}
						]}""")
				.build());

		DialogueGenerationResult result = generator.generateDialogue(List.of("careful"), "B1", "TOEIC", "vi");

		assertThat(result.lines()).containsExactly(
				new DictationDialogueLine("Narrator", "Listen carefully.", "Hãy lắng nghe cẩn thận."));
	}

	@Test
	void stripsMarkdownCodeFencesBeforeParsing() {
		when(llmClient.complete(any())).thenReturn(LlmResponse.builder()
				.content("```json\n{\"topic\": \"Study tips\", \"lines\": [{\"speaker\": \"Narrator\", \"text\": \"Listen carefully.\"}]}\n```")
				.build());

		DialogueGenerationResult result = generator.generateDialogue(List.of("careful"), null, null, null);

		assertThat(result.topic()).isEqualTo("Study tips");
		assertThat(result.lines()).containsExactly(new DictationDialogueLine("Narrator", "Listen carefully.", null));
	}

	@Test
	void fallsBackToTemplatedLinesWithNullTopicWhenLlmCallFails() {
		when(llmClient.complete(any())).thenThrow(new RestClientException("ai-service unreachable"));

		DialogueGenerationResult result = generator.generateDialogue(List.of("reluctant"), null, null, null);

		assertThat(result.topic()).isNull();
		assertThat(result.lines()).containsExactly(
				new DictationDialogueLine("Narrator", "Listen carefully and write the word \"reluctant\".", null));
	}

	@Test
	void fallsBackToTemplatedLinesWhenLlmReturnsNoUsableLines() {
		when(llmClient.complete(any())).thenReturn(LlmResponse.builder().content("{\"topic\": \"x\", \"lines\": []}").build());

		DialogueGenerationResult result = generator.generateDialogue(List.of("reluctant"), null, null, null);

		assertThat(result.topic()).isNull();
		assertThat(result.lines()).containsExactly(
				new DictationDialogueLine("Narrator", "Listen carefully and write the word \"reluctant\".", null));
	}
}
