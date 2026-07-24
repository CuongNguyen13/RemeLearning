package com.remelearning.english.speaking.library.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.remelearning.common.ai.pronunciation.PhonemePronunciationScore;
import com.remelearning.common.ai.pronunciation.PronunciationScore;
import com.remelearning.common.ai.pronunciation.PronunciationScoringClient;
import com.remelearning.common.storage.StorageClient;
import com.remelearning.common.ai.pronunciation.WordPronunciationScore;
import com.remelearning.english.speaking.dto.SpeakingPracticeItemDto;
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
import com.remelearning.english.speaking.service.SpeakingLearnService;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.util.List;

import org.mockito.ArgumentCaptor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SpeakingLibraryServiceImplTest {

	// Builds a service instance wired with the given mocks, storageClient/pronunciationScoringClient/
	// speakingLearnService left as fresh mocks (only exercised by submitSentenceAttempt/
	// generatePracticeFromSection tests) and a real ObjectMapper since weak-phoneme serialization
	// exercises real JSON (de)serialization, not a mocked one.
	private SpeakingLibraryServiceImpl newService(
			SpeakingLibraryTopicMapper topicMapper,
			SpeakingLibrarySectionMapper sectionMapper,
			SpeakingLibrarySentenceMapper sentenceMapper,
			SpeakingTopicProgressMapper progressMapper,
			SpeakingLibraryAttemptMapper attemptMapper,
			LlmSpeakingLibraryGenerator generator) {
		return newService(topicMapper, sectionMapper, sentenceMapper, progressMapper, attemptMapper, generator,
				mock(PronunciationScoringClient.class), mock(StorageClient.class), mock(SpeakingLearnService.class));
	}

	private SpeakingLibraryServiceImpl newService(
			SpeakingLibraryTopicMapper topicMapper,
			SpeakingLibrarySectionMapper sectionMapper,
			SpeakingLibrarySentenceMapper sentenceMapper,
			SpeakingTopicProgressMapper progressMapper,
			SpeakingLibraryAttemptMapper attemptMapper,
			LlmSpeakingLibraryGenerator generator,
			PronunciationScoringClient pronunciationScoringClient,
			StorageClient storageClient) {
		return newService(topicMapper, sectionMapper, sentenceMapper, progressMapper, attemptMapper, generator,
				pronunciationScoringClient, storageClient, mock(SpeakingLearnService.class));
	}

	private SpeakingLibraryServiceImpl newService(
			SpeakingLibraryTopicMapper topicMapper,
			SpeakingLibrarySectionMapper sectionMapper,
			SpeakingLibrarySentenceMapper sentenceMapper,
			SpeakingTopicProgressMapper progressMapper,
			SpeakingLibraryAttemptMapper attemptMapper,
			LlmSpeakingLibraryGenerator generator,
			PronunciationScoringClient pronunciationScoringClient,
			StorageClient storageClient,
			SpeakingLearnService speakingLearnService) {
		return new SpeakingLibraryServiceImpl(
				topicMapper, sectionMapper, sentenceMapper, progressMapper, attemptMapper, generator,
				pronunciationScoringClient, storageClient, new ObjectMapper(), "en", speakingLearnService);
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
	void submitSentenceAttemptPersistsWeakPhonemesFromScoringResponse() throws Exception {
		SpeakingLibraryTopicMapper topicMapper = mock(SpeakingLibraryTopicMapper.class);
		SpeakingLibrarySectionMapper sectionMapper = mock(SpeakingLibrarySectionMapper.class);
		SpeakingLibrarySentenceMapper sentenceMapper = mock(SpeakingLibrarySentenceMapper.class);
		SpeakingTopicProgressMapper progressMapper = mock(SpeakingTopicProgressMapper.class);
		SpeakingLibraryAttemptMapper attemptMapper = mock(SpeakingLibraryAttemptMapper.class);
		LlmSpeakingLibraryGenerator generator = mock(LlmSpeakingLibraryGenerator.class);
		PronunciationScoringClient scoringClient = mock(PronunciationScoringClient.class);
		StorageClient storageClient = mock(StorageClient.class);

		SpeakingLibrarySection section = new SpeakingLibrarySection();
		section.setId(100L); section.setTopicId(1L);
		when(sectionMapper.findById(100L)).thenReturn(section);
		SpeakingLibrarySentence sentence = new SpeakingLibrarySentence();
		sentence.setId(5L); sentence.setSectionId(100L); sentence.setSentenceText("Think about the weather.");
		when(sentenceMapper.findById(5L)).thenReturn(sentence);

		when(storageClient.read(anyString())).thenReturn(new ByteArrayInputStream("wav".getBytes()));
		// Mirrors SpeakingLearnServiceImpl.submit's mocked scoring response shape - weakPhonemes is
		// already computed by ai-service's GOP scorer (WEAK_PHONEME_THRESHOLD = 0.4), not re-derived
		// here, so the response simply carries the phonemes below that threshold straight through.
		when(scoringClient.score(any(), anyString(), eq("Think about the weather."), eq("en"))).thenReturn(
				new PronunciationScore(0.72, List.of(
						new WordPronunciationScore("Think", 0.3, List.of(new PhonemePronunciationScore("θ", 0.2))),
						new WordPronunciationScore("weather", 0.9, List.of(new PhonemePronunciationScore("ð", 0.9)))),
						"Sink about the weather.", List.of("θ")));

		org.springframework.mock.web.MockMultipartFile audio =
				new org.springframework.mock.web.MockMultipartFile("audio", "attempt.wav", "audio/wav", "fake-audio".getBytes());

		SpeakingLibraryServiceImpl service = newService(topicMapper, sectionMapper, sentenceMapper, progressMapper,
				attemptMapper, generator, scoringClient, storageClient);

		service.submitSentenceAttempt("user-1", 100L, 5L, audio);

		ArgumentCaptor<SpeakingLibraryAttempt> captor = ArgumentCaptor.forClass(SpeakingLibraryAttempt.class);
		verify(attemptMapper).insert(captor.capture());
		assertThat(captor.getValue().getWeakPhonemesJson()).isEqualTo("[\"θ\"]");
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
		when(attemptMapper.findBySectionIdAndUserId(100L, "user-1")).thenReturn(List.of(lowAttempt, passAttempt1, passAttempt2));

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
		when(attemptMapper.findBySectionIdAndUserId(100L, "user-1")).thenReturn(List.of(passAttempt1));

		SpeakingLibraryServiceImpl service = newService(topicMapper, sectionMapper, sentenceMapper, progressMapper, attemptMapper, generator);

		var response = service.finishSection("user-1", 100L);

		assertThat(response.getPassedSentences()).isEqualTo(1);
		assertThat(response.isPassed()).isFalse();
		assertThat(response.getNextTopicId()).isNull();
		verify(progressMapper, never()).markPassed(any(), any());
	}

	@Test
	void finishSectionDoesNotCreditOtherLearnersPassingAttemptsToTheCallingUser() {
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

		// Learner A ("user-a") has passing attempts on BOTH sentences of this shared section. Learner
		// B ("user-b"), the caller here, has a passing attempt only on sentence 1 and none at all on
		// sentence 2 - so Learner B must NOT be marked as having passed the section, even though the
		// section (across all learners) has a passing attempt for every sentence.
		SpeakingLibraryAttempt learnerAPassSentence1 = SpeakingLibraryAttempt.builder()
				.userId("user-a").sectionId(100L).sentenceId(1L).phonemeScore(0.9).wordScore(0.9).build();
		SpeakingLibraryAttempt learnerAPassSentence2 = SpeakingLibraryAttempt.builder()
				.userId("user-a").sectionId(100L).sentenceId(2L).phonemeScore(0.9).wordScore(0.9).build();
		SpeakingLibraryAttempt learnerBPassSentence1 = SpeakingLibraryAttempt.builder()
				.userId("user-b").sectionId(100L).sentenceId(1L).phonemeScore(0.85).wordScore(0.85).build();

		// findBySectionId (unscoped) would return every learner's rows - only stubbed here to prove
		// the fix no longer calls it for this decision; the scoped query below is what finishSection
		// must actually use for user-b.
		when(attemptMapper.findBySectionId(100L)).thenReturn(
				List.of(learnerAPassSentence1, learnerAPassSentence2, learnerBPassSentence1));
		when(attemptMapper.findBySectionIdAndUserId(100L, "user-b")).thenReturn(List.of(learnerBPassSentence1));

		SpeakingLibraryServiceImpl service = newService(topicMapper, sectionMapper, sentenceMapper, progressMapper, attemptMapper, generator);

		var response = service.finishSection("user-b", 100L);

		assertThat(response.getTotalSentences()).isEqualTo(2);
		assertThat(response.getPassedSentences()).isEqualTo(1);
		assertThat(response.isPassed()).isFalse();
		assertThat(response.getNextTopicId()).isNull();
		verify(progressMapper, never()).markPassed(any(), any());
	}

	@Test
	void generatePracticeFromSectionThrowsWhenSectionDoesNotExist() {
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
				() -> service.generatePracticeFromSection("user-1", 404L));
	}

	@Test
	void generatePracticeFromSectionReturnsEmptyListWhenLearnerHasNoAttemptsOnThisSection() {
		SpeakingLibraryTopicMapper topicMapper = mock(SpeakingLibraryTopicMapper.class);
		SpeakingLibrarySectionMapper sectionMapper = mock(SpeakingLibrarySectionMapper.class);
		SpeakingLibrarySentenceMapper sentenceMapper = mock(SpeakingLibrarySentenceMapper.class);
		SpeakingTopicProgressMapper progressMapper = mock(SpeakingTopicProgressMapper.class);
		SpeakingLibraryAttemptMapper attemptMapper = mock(SpeakingLibraryAttemptMapper.class);
		LlmSpeakingLibraryGenerator generator = mock(LlmSpeakingLibraryGenerator.class);
		SpeakingLearnService speakingLearnService = mock(SpeakingLearnService.class);

		SpeakingLibrarySection section = new SpeakingLibrarySection();
		section.setId(100L); section.setTopicId(1L);
		when(sectionMapper.findById(100L)).thenReturn(section);
		when(attemptMapper.findBySectionIdAndUserId(100L, "user-1")).thenReturn(List.of());

		SpeakingLibraryServiceImpl service = newService(topicMapper, sectionMapper, sentenceMapper, progressMapper,
				attemptMapper, generator, mock(PronunciationScoringClient.class), mock(StorageClient.class), speakingLearnService);

		List<SpeakingPracticeItemDto> result = service.generatePracticeFromSection("user-1", 100L);

		assertThat(result).isEmpty();
		verify(speakingLearnService, never()).generatePracticeForKeywords(any(), any(), any(), any());
	}

	@Test
	void generatePracticeFromSectionReturnsEmptyListWhenNoAttemptHadAMispronouncedPhoneme() {
		SpeakingLibraryTopicMapper topicMapper = mock(SpeakingLibraryTopicMapper.class);
		SpeakingLibrarySectionMapper sectionMapper = mock(SpeakingLibrarySectionMapper.class);
		SpeakingLibrarySentenceMapper sentenceMapper = mock(SpeakingLibrarySentenceMapper.class);
		SpeakingTopicProgressMapper progressMapper = mock(SpeakingTopicProgressMapper.class);
		SpeakingLibraryAttemptMapper attemptMapper = mock(SpeakingLibraryAttemptMapper.class);
		LlmSpeakingLibraryGenerator generator = mock(LlmSpeakingLibraryGenerator.class);
		SpeakingLearnService speakingLearnService = mock(SpeakingLearnService.class);

		SpeakingLibrarySection section = new SpeakingLibrarySection();
		section.setId(100L); section.setTopicId(1L);
		when(sectionMapper.findById(100L)).thenReturn(section);
		SpeakingLibraryAttempt cleanAttempt = SpeakingLibraryAttempt.builder()
				.userId("user-1").sectionId(100L).sentenceId(1L).weakPhonemesJson("[]").build();
		when(attemptMapper.findBySectionIdAndUserId(100L, "user-1")).thenReturn(List.of(cleanAttempt));

		SpeakingLibraryServiceImpl service = newService(topicMapper, sectionMapper, sentenceMapper, progressMapper,
				attemptMapper, generator, mock(PronunciationScoringClient.class), mock(StorageClient.class), speakingLearnService);

		List<SpeakingPracticeItemDto> result = service.generatePracticeFromSection("user-1", 100L);

		assertThat(result).isEmpty();
		verify(speakingLearnService, never()).generatePracticeForKeywords(any(), any(), any(), any());
	}

	@Test
	void generatePracticeFromSectionUnionsWeakPhonemesAcrossAllOfThisLearnersSentenceAttempts() {
		SpeakingLibraryTopicMapper topicMapper = mock(SpeakingLibraryTopicMapper.class);
		SpeakingLibrarySectionMapper sectionMapper = mock(SpeakingLibrarySectionMapper.class);
		SpeakingLibrarySentenceMapper sentenceMapper = mock(SpeakingLibrarySentenceMapper.class);
		SpeakingTopicProgressMapper progressMapper = mock(SpeakingTopicProgressMapper.class);
		SpeakingLibraryAttemptMapper attemptMapper = mock(SpeakingLibraryAttemptMapper.class);
		LlmSpeakingLibraryGenerator generator = mock(LlmSpeakingLibraryGenerator.class);
		SpeakingLearnService speakingLearnService = mock(SpeakingLearnService.class);

		SpeakingLibrarySection section = new SpeakingLibrarySection();
		section.setId(100L); section.setTopicId(1L);
		when(sectionMapper.findById(100L)).thenReturn(section);
		SpeakingLibraryTopic topic = new SpeakingLibraryTopic();
		topic.setId(1L); topic.setName("Travel"); topic.setLevel("B1");
		when(topicMapper.findById(1L)).thenReturn(topic);

		// Two sentences in this section, each with their own attempt from this learner. A different
		// learner's attempt on this same section is deliberately NOT stubbed on
		// findBySectionIdAndUserId("user-1", ...) - it lives under a different (sectionId, userId) key,
		// proving the mapper-level scoping keeps it out of this union without needing a Java-side filter.
		SpeakingLibraryAttempt sentence1Attempt = SpeakingLibraryAttempt.builder()
				.userId("user-1").sectionId(100L).sentenceId(1L).weakPhonemesJson("[\"θ\"]").build();
		SpeakingLibraryAttempt sentence2Attempt = SpeakingLibraryAttempt.builder()
				.userId("user-1").sectionId(100L).sentenceId(2L).weakPhonemesJson("[\"ð\", \"θ\"]").build();
		SpeakingLibraryAttempt otherLearnerAttempt = SpeakingLibraryAttempt.builder()
				.userId("user-2").sectionId(100L).sentenceId(1L).weakPhonemesJson("[\"r\"]").build();
		when(attemptMapper.findBySectionIdAndUserId(100L, "user-1")).thenReturn(List.of(sentence1Attempt, sentence2Attempt));
		when(attemptMapper.findBySectionIdAndUserId(100L, "user-2")).thenReturn(List.of(otherLearnerAttempt));

		List<SpeakingPracticeItemDto> generated = List.of(SpeakingPracticeItemDto.builder().practiceItemId(9L).build());
		when(speakingLearnService.generatePracticeForKeywords("user-1", List.of("θ", "ð"), "B1", null))
				.thenReturn(generated);

		SpeakingLibraryServiceImpl service = newService(topicMapper, sectionMapper, sentenceMapper, progressMapper,
				attemptMapper, generator, mock(PronunciationScoringClient.class), mock(StorageClient.class), speakingLearnService);

		List<SpeakingPracticeItemDto> result = service.generatePracticeFromSection("user-1", 100L);

		assertThat(result).isEqualTo(generated);
		verify(speakingLearnService).generatePracticeForKeywords("user-1", List.of("θ", "ð"), "B1", null);
	}
}
