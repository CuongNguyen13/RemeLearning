package com.remelearning.english.listening.library.generator;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.remelearning.common.storage.StorageClient;
import com.remelearning.english.learn.common.AiContentClient;
import com.remelearning.english.learn.common.AiContentException;
import com.remelearning.english.learn.common.DialogueAudioSynthesizer;
import com.remelearning.english.learn.common.DialogueLine;
import com.remelearning.english.learn.common.SynthesizedDialogue;
import com.remelearning.english.listening.library.domain.ListeningLibraryQuestion;
import com.remelearning.english.listening.library.domain.ListeningLibrarySection;
import com.remelearning.english.listening.library.domain.ListeningLibraryTopic;
import com.remelearning.english.listening.library.mapper.ListeningLibraryQuestionMapper;
import com.remelearning.english.listening.library.mapper.ListeningLibrarySectionMapper;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class LlmListeningLibraryGeneratorTest {

	private final AiContentClient aiContentClient = mock(AiContentClient.class);
	private final DialogueAudioSynthesizer audioSynthesizer = mock(DialogueAudioSynthesizer.class);
	private final StorageClient storageClient = mock(StorageClient.class);
	private final ListeningLibrarySectionMapper sectionMapper = mock(ListeningLibrarySectionMapper.class);
	private final ListeningLibraryQuestionMapper questionMapper = mock(ListeningLibraryQuestionMapper.class);

	private final LlmListeningLibraryGenerator generator = new LlmListeningLibraryGenerator(
			aiContentClient, audioSynthesizer, storageClient, sectionMapper, questionMapper, "en");

	@Test
	void generateSectionPersistsPassageQuestionsAndSynthesizedAudio() throws Exception {
		String llmJson = """
				{
				  "passage": "A short passage about travel.",
				  "questions": [
				    {"question": "Where did they go?", "options": ["Paris", "Rome", "Tokyo", "Cairo"], "correctOption": "A", "explanation": "Stated in the passage."}
				  ]
				}
				""";
		// Real AiContentClient signature: completeJson(systemPrompt, userPrompt, temperature, maxOutputTokens, Class<T>).
		when(aiContentClient.completeJson(anyString(), anyString(), anyDouble(), anyInt(), eq(LlmListeningLibraryGenerator.LlmPayload.class)))
				.thenAnswer(invocation -> new ObjectMapper().readValue(llmJson, LlmListeningLibraryGenerator.LlmPayload.class));
		when(audioSynthesizer.synthesize(any(), eq("en")))
				.thenReturn(new SynthesizedDialogue("fake-audio-bytes".getBytes(), "A short passage about travel.", null));

		ListeningLibraryTopic topic = ListeningLibraryTopic.builder().id(1L).name("Travel").level("A2").build();

		ListeningLibrarySection section = generator.generateSection(topic);

		assertThat(section.getPassageText()).isEqualTo("A short passage about travel.");
		assertThat(section.getTopicId()).isEqualTo(1L);
		assertThat(section.getAudioStorageKey()).isNotBlank();
		verify(sectionMapper).insert(any(ListeningLibrarySection.class));
		verify(questionMapper).insert(any(ListeningLibraryQuestion.class));
		verify(audioSynthesizer).synthesize(List.of(new DialogueLine("Narrator", "A short passage about travel.", null)), "en");
	}

	@Test
	void generateSectionThrowsRatherThanPersistingWhenTheLlmReturnsNoPassage() {
		when(aiContentClient.completeJson(anyString(), anyString(), anyDouble(), anyInt(), eq(LlmListeningLibraryGenerator.LlmPayload.class)))
				.thenThrow(new AiContentException("LLM call failed", new RuntimeException("boom")));

		ListeningLibraryTopic topic = ListeningLibraryTopic.builder().id(1L).name("Travel").level("A2").build();

		assertThatThrownBy(() -> generator.generateSection(topic)).isInstanceOf(AiContentException.class);
	}
}
