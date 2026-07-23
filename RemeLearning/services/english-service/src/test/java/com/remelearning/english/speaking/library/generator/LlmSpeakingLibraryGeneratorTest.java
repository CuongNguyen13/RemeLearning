package com.remelearning.english.speaking.library.generator;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.remelearning.common.storage.StorageClient;
import com.remelearning.english.learn.common.AiContentClient;
import com.remelearning.english.learn.common.AiContentException;
import com.remelearning.english.learn.common.DialogueAudioSynthesizer;
import com.remelearning.english.learn.common.DialogueLine;
import com.remelearning.english.learn.common.SynthesizedDialogue;
import com.remelearning.english.speaking.library.domain.SpeakingLibrarySection;
import com.remelearning.english.speaking.library.domain.SpeakingLibrarySentence;
import com.remelearning.english.speaking.library.domain.SpeakingLibraryTopic;
import com.remelearning.english.speaking.library.mapper.SpeakingLibrarySectionMapper;
import com.remelearning.english.speaking.library.mapper.SpeakingLibrarySentenceMapper;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class LlmSpeakingLibraryGeneratorTest {

	private final AiContentClient aiContentClient = mock(AiContentClient.class);
	private final DialogueAudioSynthesizer audioSynthesizer = mock(DialogueAudioSynthesizer.class);
	private final StorageClient storageClient = mock(StorageClient.class);
	private final SpeakingLibrarySectionMapper sectionMapper = mock(SpeakingLibrarySectionMapper.class);
	private final SpeakingLibrarySentenceMapper sentenceMapper = mock(SpeakingLibrarySentenceMapper.class);

	private final LlmSpeakingLibraryGenerator generator = new LlmSpeakingLibraryGenerator(
			aiContentClient, audioSynthesizer, storageClient, sectionMapper, sentenceMapper, "en");

	@Test
	void generateSectionPersistsSentencesAndSynthesizedAudioPerSentence() throws Exception {
		String llmJson = """
				{
				  "sentences": [
				    {"text": "I usually wake up at seven.", "ipa": "/aɪ ˈjuːʒuəli weɪk ʌp æt ˈsevən/"},
				    {"text": "She travels to Paris every summer.", "ipa": "/ʃiː ˈtrævəlz tuː ˈpærɪs ˈevri ˈsʌmər/"}
				  ]
				}
				""";
		// Real AiContentClient signature: completeJson(systemPrompt, userPrompt, temperature, maxOutputTokens, Class<T>).
		when(aiContentClient.completeJson(anyString(), anyString(), anyDouble(), anyInt(), eq(LlmSpeakingLibraryGenerator.LlmPayload.class)))
				.thenAnswer(invocation -> new ObjectMapper().readValue(llmJson, LlmSpeakingLibraryGenerator.LlmPayload.class));
		when(audioSynthesizer.synthesize(any(), eq("en")))
				.thenReturn(new SynthesizedDialogue("fake-audio-bytes".getBytes(), "narrated text", null));

		SpeakingLibraryTopic topic = SpeakingLibraryTopic.builder().id(1L).name("Travel").level("A2").build();

		SpeakingLibrarySection section = generator.generateSection(topic);

		assertThat(section.getTopicId()).isEqualTo(1L);
		verify(sectionMapper).insert(any(SpeakingLibrarySection.class));

		// Each sentence must be synthesized individually (one sample clip per sentence, not one for
		// the whole section) and persisted with its own audio key + IPA.
		verify(audioSynthesizer, times(2)).synthesize(any(), eq("en"));
		verify(audioSynthesizer).synthesize(List.of(new DialogueLine("Narrator", "I usually wake up at seven.", null)), "en");
		verify(audioSynthesizer).synthesize(List.of(new DialogueLine("Narrator", "She travels to Paris every summer.", null)), "en");

		ArgumentCaptor<SpeakingLibrarySentence> sentenceCaptor = ArgumentCaptor.forClass(SpeakingLibrarySentence.class);
		verify(sentenceMapper, times(2)).insert(sentenceCaptor.capture());
		List<SpeakingLibrarySentence> persisted = sentenceCaptor.getAllValues();
		assertThat(persisted).extracting(SpeakingLibrarySentence::getSentenceText)
				.containsExactly("I usually wake up at seven.", "She travels to Paris every summer.");
		assertThat(persisted).extracting(SpeakingLibrarySentence::getIpa)
				.containsExactly("/aɪ ˈjuːʒuəli weɪk ʌp æt ˈsevən/", "/ʃiː ˈtrævəlz tuː ˈpærɪs ˈevri ˈsʌmər/");
		assertThat(persisted).allSatisfy(sentence -> assertThat(sentence.getSampleAudioStorageKey()).isNotBlank());
	}

	@Test
	void generateSectionThrowsRatherThanPersistingWhenTheLlmReturnsNoSentences() {
		when(aiContentClient.completeJson(anyString(), anyString(), anyDouble(), anyInt(), eq(LlmSpeakingLibraryGenerator.LlmPayload.class)))
				.thenThrow(new AiContentException("LLM call failed", new RuntimeException("boom")));

		SpeakingLibraryTopic topic = SpeakingLibraryTopic.builder().id(1L).name("Travel").level("A2").build();

		assertThatThrownBy(() -> generator.generateSection(topic)).isInstanceOf(AiContentException.class);
		verify(sectionMapper, never()).insert(any());
		verify(sentenceMapper, never()).insert(any());
	}
}
