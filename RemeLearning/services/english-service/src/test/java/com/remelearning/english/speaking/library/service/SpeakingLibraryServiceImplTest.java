package com.remelearning.english.speaking.library.service;

import com.remelearning.common.ai.pronunciation.PronunciationScoringClient;
import com.remelearning.english.speaking.library.domain.SpeakingLibraryAttempt;
import com.remelearning.english.speaking.library.domain.SpeakingLibrarySection;
import com.remelearning.english.speaking.library.domain.SpeakingLibrarySentence;
import com.remelearning.english.speaking.library.domain.SpeakingLibraryTopic;
import com.remelearning.english.speaking.library.domain.SpeakingTopicProgress;
import com.remelearning.english.speaking.library.domain.SpeakingTopicStatus;
import com.remelearning.english.speaking.library.generator.LlmSpeakingLibraryGenerator;
import com.remelearning.english.speaking.library.mapper.SpeakingLibraryAttemptMapper;
import com.remelearning.english.speaking.library.mapper.SpeakingLibrarySectionMapper;
import com.remelearning.english.speaking.library.mapper.SpeakingLibrarySentenceMapper;
import com.remelearning.english.speaking.library.mapper.SpeakingLibraryTopicMapper;
import com.remelearning.english.speaking.library.mapper.SpeakingTopicProgressMapper;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SpeakingLibraryServiceImplTest {

	// Builds a service instance wired with the given mocks, storageClient/pronunciationScoringClient
	// left null since none of the five behaviors under test here reach either (the section/topic
	// existence and gating checks all fail-fast before touching audio storage/scoring).
	private SpeakingLibraryServiceImpl newService(
			SpeakingLibraryTopicMapper topicMapper,
			SpeakingLibrarySectionMapper sectionMapper,
			SpeakingLibrarySentenceMapper sentenceMapper,
			SpeakingTopicProgressMapper progressMapper,
			SpeakingLibraryAttemptMapper attemptMapper,
			LlmSpeakingLibraryGenerator generator) {
		return new SpeakingLibraryServiceImpl(
				topicMapper, sectionMapper, sentenceMapper, progressMapper, attemptMapper, generator,
				mock(PronunciationScoringClient.class), null, "en");
	}

	@Test
	void getTopicsBootstrapsFirstTopicAndDefaultsMissingRowsToLocked() {
		SpeakingLibraryTopicMapper topicMapper = mock(SpeakingLibraryTopicMapper.class);
		SpeakingLibrarySectionMapper sectionMapper = mock(SpeakingLibrarySectionMapper.class);
		SpeakingLibrarySentenceMapper sentenceMapper = mock(SpeakingLibrarySentenceMapper.class);
		SpeakingTopicProgressMapper progressMapper = mock(SpeakingTopicProgressMapper.class);
		SpeakingLibraryAttemptMapper attemptMapper = mock(SpeakingLibraryAttemptMapper.class);
		LlmSpeakingLibraryGenerator generator = mock(LlmSpeakingLibraryGenerator.class);

		SpeakingLibraryTopic topic1 = new SpeakingLibraryTopic();
		topic1.setId(1L); topic1.setName("Travel"); topic1.setLevel("A1"); topic1.setSequenceOrder(1);
		SpeakingLibraryTopic topic2 = new SpeakingLibraryTopic();
		topic2.setId(2L); topic2.setName("Food"); topic2.setLevel("A1"); topic2.setSequenceOrder(2);

		when(topicMapper.findAll()).thenReturn(List.of(topic1, topic2));
		when(topicMapper.findBySequenceOrder(1)).thenReturn(topic1);
		when(progressMapper.findByUserId("user-1")).thenReturn(List.of());

		SpeakingLibraryServiceImpl service = newService(topicMapper, sectionMapper, sentenceMapper, progressMapper, attemptMapper, generator);

		var result = service.getTopics("user-1");

		verify(progressMapper).bootstrapFirstTopic("user-1", 1L);
		assertThat(result).hasSize(2);
		assertThat(result.get(1).getStatus()).isEqualTo("LOCKED");
	}

	@Test
	void startOrResumeSectionThrowsWhenTopicIsLocked() {
		SpeakingLibraryTopicMapper topicMapper = mock(SpeakingLibraryTopicMapper.class);
		SpeakingLibrarySectionMapper sectionMapper = mock(SpeakingLibrarySectionMapper.class);
		SpeakingLibrarySentenceMapper sentenceMapper = mock(SpeakingLibrarySentenceMapper.class);
		SpeakingTopicProgressMapper progressMapper = mock(SpeakingTopicProgressMapper.class);
		SpeakingLibraryAttemptMapper attemptMapper = mock(SpeakingLibraryAttemptMapper.class);
		LlmSpeakingLibraryGenerator generator = mock(LlmSpeakingLibraryGenerator.class);

		SpeakingLibraryTopic topic = new SpeakingLibraryTopic();
		topic.setId(1L); topic.setSequenceOrder(1);
		when(topicMapper.findById(1L)).thenReturn(topic);

		SpeakingTopicProgress progress = SpeakingTopicProgress.builder()
				.userId("user-1").topicId(1L).status(SpeakingTopicStatus.LOCKED).build();
		when(progressMapper.findByUserIdAndTopicId("user-1", 1L)).thenReturn(progress);

		SpeakingLibraryServiceImpl service = newService(topicMapper, sectionMapper, sentenceMapper, progressMapper, attemptMapper, generator);

		org.junit.jupiter.api.Assertions.assertThrows(
				com.remelearning.common.exception.BusinessException.class,
				() -> service.startOrResumeSection("user-1", 1L));
	}

	@Test
	void startOrResumeSectionThrowsWhenTopicDoesNotExist() {
		SpeakingLibraryTopicMapper topicMapper = mock(SpeakingLibraryTopicMapper.class);
		SpeakingLibrarySectionMapper sectionMapper = mock(SpeakingLibrarySectionMapper.class);
		SpeakingLibrarySentenceMapper sentenceMapper = mock(SpeakingLibrarySentenceMapper.class);
		SpeakingTopicProgressMapper progressMapper = mock(SpeakingTopicProgressMapper.class);
		SpeakingLibraryAttemptMapper attemptMapper = mock(SpeakingLibraryAttemptMapper.class);
		LlmSpeakingLibraryGenerator generator = mock(LlmSpeakingLibraryGenerator.class);

		when(topicMapper.findById(999L)).thenReturn(null);

		SpeakingLibraryServiceImpl service = newService(topicMapper, sectionMapper, sentenceMapper, progressMapper, attemptMapper, generator);

		org.junit.jupiter.api.Assertions.assertThrows(
				com.remelearning.common.exception.BusinessException.class,
				() -> service.startOrResumeSection("user-1", 999L));

		verify(progressMapper, never()).findByUserIdAndTopicId(any(), any());
	}

	@Test
	void submitSentenceAttemptThrowsWhenSectionDoesNotExist() {
		SpeakingLibraryTopicMapper topicMapper = mock(SpeakingLibraryTopicMapper.class);
		SpeakingLibrarySectionMapper sectionMapper = mock(SpeakingLibrarySectionMapper.class);
		SpeakingLibrarySentenceMapper sentenceMapper = mock(SpeakingLibrarySentenceMapper.class);
		SpeakingTopicProgressMapper progressMapper = mock(SpeakingTopicProgressMapper.class);
		SpeakingLibraryAttemptMapper attemptMapper = mock(SpeakingLibraryAttemptMapper.class);
		LlmSpeakingLibraryGenerator generator = mock(LlmSpeakingLibraryGenerator.class);

		when(sectionMapper.findById(404L)).thenReturn(null);

		SpeakingLibraryServiceImpl service = newService(topicMapper, sectionMapper, sentenceMapper, progressMapper, attemptMapper, generator);

		org.junit.jupiter.api.Assertions.assertThrows(
				com.remelearning.common.exception.BusinessException.class,
				() -> service.submitSentenceAttempt("user-1", 404L, 1L, null));

		verify(sentenceMapper, never()).findById(any());
	}

	@Test
	void finishSectionMarksTopicPassedAndUnlocksNextTopicWhenEverySentencePassesAboveThreshold() {
		SpeakingLibraryTopicMapper topicMapper = mock(SpeakingLibraryTopicMapper.class);
		SpeakingLibrarySectionMapper sectionMapper = mock(SpeakingLibrarySectionMapper.class);
		SpeakingLibrarySentenceMapper sentenceMapper = mock(SpeakingLibrarySentenceMapper.class);
		SpeakingTopicProgressMapper progressMapper = mock(SpeakingTopicProgressMapper.class);
		SpeakingLibraryAttemptMapper attemptMapper = mock(SpeakingLibraryAttemptMapper.class);
		LlmSpeakingLibraryGenerator generator = mock(LlmSpeakingLibraryGenerator.class);

		SpeakingLibraryTopic topic = new SpeakingLibraryTopic();
		topic.setId(1L); topic.setSequenceOrder(1);
		SpeakingLibrarySection section = new SpeakingLibrarySection();
		section.setId(100L); section.setTopicId(1L);
		when(sectionMapper.findById(100L)).thenReturn(section);
		when(topicMapper.findById(1L)).thenReturn(topic);

		SpeakingLibraryTopic nextTopic = new SpeakingLibraryTopic();
		nextTopic.setId(2L); nextTopic.setSequenceOrder(2);
		when(topicMapper.findBySequenceOrder(2)).thenReturn(nextTopic);
		when(progressMapper.findByUserIdAndTopicId("user-1", 2L)).thenReturn(
				SpeakingTopicProgress.builder().userId("user-1").topicId(2L)
						.status(SpeakingTopicStatus.UNLOCKED).build());

		SpeakingLibrarySentence sentence1 = new SpeakingLibrarySentence();
		sentence1.setId(1L); sentence1.setSectionId(100L);
		SpeakingLibrarySentence sentence2 = new SpeakingLibrarySentence();
		sentence2.setId(2L); sentence2.setSectionId(100L);
		when(sentenceMapper.findBySectionId(100L)).thenReturn(List.of(sentence1, sentence2));

		// Sentence 1 has one low-scoring attempt followed by one passing attempt (at least one
		// passing attempt is enough); sentence 2 has a single passing attempt.
		SpeakingLibraryAttempt lowAttempt = SpeakingLibraryAttempt.builder()
				.sentenceId(1L).phonemeScore(0.4).wordScore(0.5).build();
		SpeakingLibraryAttempt passAttempt1 = SpeakingLibraryAttempt.builder()
				.sentenceId(1L).phonemeScore(0.85).wordScore(0.9).build();
		SpeakingLibraryAttempt passAttempt2 = SpeakingLibraryAttempt.builder()
				.sentenceId(2L).phonemeScore(0.75).wordScore(0.8).build();
		when(attemptMapper.findBySectionId(100L)).thenReturn(List.of(lowAttempt, passAttempt1, passAttempt2));

		SpeakingLibraryServiceImpl service = newService(topicMapper, sectionMapper, sentenceMapper, progressMapper, attemptMapper, generator);

		var response = service.finishSection("user-1", 100L);

		assertThat(response.getTotalSentences()).isEqualTo(2);
		assertThat(response.getPassedSentences()).isEqualTo(2);
		assertThat(response.isPassed()).isTrue();
		assertThat(response.getNextTopicId()).isEqualTo(2L);
		assertThat(response.isNextTopicUnlocked()).isTrue();
		verify(progressMapper).markPassed("user-1", 1L);
		verify(progressMapper).unlockIfLocked("user-1", 2L);
	}

	@Test
	void finishSectionDoesNotPassTopicWhenAnySentenceHasNoQualifyingAttempt() {
		SpeakingLibraryTopicMapper topicMapper = mock(SpeakingLibraryTopicMapper.class);
		SpeakingLibrarySectionMapper sectionMapper = mock(SpeakingLibrarySectionMapper.class);
		SpeakingLibrarySentenceMapper sentenceMapper = mock(SpeakingLibrarySentenceMapper.class);
		SpeakingTopicProgressMapper progressMapper = mock(SpeakingTopicProgressMapper.class);
		SpeakingLibraryAttemptMapper attemptMapper = mock(SpeakingLibraryAttemptMapper.class);
		LlmSpeakingLibraryGenerator generator = mock(LlmSpeakingLibraryGenerator.class);

		SpeakingLibrarySection section = new SpeakingLibrarySection();
		section.setId(100L); section.setTopicId(1L);
		when(sectionMapper.findById(100L)).thenReturn(section);

		SpeakingLibrarySentence sentence1 = new SpeakingLibrarySentence();
		sentence1.setId(1L); sentence1.setSectionId(100L);
		SpeakingLibrarySentence sentence2 = new SpeakingLibrarySentence();
		sentence2.setId(2L); sentence2.setSectionId(100L);
		when(sentenceMapper.findBySectionId(100L)).thenReturn(List.of(sentence1, sentence2));

		// Only sentence 1 has a passing attempt; sentence 2 has none at all.
		SpeakingLibraryAttempt passAttempt1 = SpeakingLibraryAttempt.builder()
				.sentenceId(1L).phonemeScore(0.85).wordScore(0.9).build();
		when(attemptMapper.findBySectionId(100L)).thenReturn(List.of(passAttempt1));

		SpeakingLibraryServiceImpl service = newService(topicMapper, sectionMapper, sentenceMapper, progressMapper, attemptMapper, generator);

		var response = service.finishSection("user-1", 100L);

		assertThat(response.getPassedSentences()).isEqualTo(1);
		assertThat(response.isPassed()).isFalse();
		assertThat(response.getNextTopicId()).isNull();
		verify(progressMapper, never()).markPassed(any(), any());
	}
}
