package com.remelearning.english.vocabulary.library.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.remelearning.common.ai.tts.TtsAudio;
import com.remelearning.common.ai.tts.TtsClient;
import com.remelearning.common.exception.BusinessException;
import com.remelearning.common.storage.StorageClient;
import com.remelearning.english.practice.service.PracticeService;
import com.remelearning.english.vocabulary.domain.VocabularyType;
import com.remelearning.english.vocabulary.library.domain.TopicMasterySummaryRow;
import com.remelearning.english.vocabulary.library.domain.VocabularyLibraryWord;
import com.remelearning.english.vocabulary.library.domain.VocabularyTopic;
import com.remelearning.english.vocabulary.library.dto.StartSectionRequest;
import com.remelearning.english.vocabulary.library.dto.TopicSummaryDto;
import com.remelearning.english.vocabulary.library.generator.GeneratedLibraryWord;
import com.remelearning.english.vocabulary.library.generator.LibraryWordGenerator;
import com.remelearning.english.vocabulary.library.mapper.VocabularyLibraryWordMapper;
import com.remelearning.english.vocabulary.library.mapper.VocabularySectionMapper;
import com.remelearning.english.vocabulary.library.mapper.VocabularyTopicMapper;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class VocabularyLibraryServiceImplTest {

	private final VocabularyTopicMapper topicMapper = mock(VocabularyTopicMapper.class);
	private final VocabularyLibraryWordMapper libraryWordMapper = mock(VocabularyLibraryWordMapper.class);
	private final VocabularySectionMapper sectionMapper = mock(VocabularySectionMapper.class);
	private final LibraryWordGenerator libraryWordGenerator = mock(LibraryWordGenerator.class);
	private final TtsClient ttsClient = mock(TtsClient.class);
	private final StorageClient storageClient = mock(StorageClient.class);
	private final PracticeService practiceService = mock(PracticeService.class);
	private final ObjectMapper objectMapper = new ObjectMapper();
	private final VocabularyLibraryServiceImpl service = new VocabularyLibraryServiceImpl(
			topicMapper, libraryWordMapper, sectionMapper, libraryWordGenerator, ttsClient, storageClient, practiceService, objectMapper);

	@Test
	void listTopicsMergesTopicRowsWithTheLearnersMasterySummary() {
		when(topicMapper.findAll()).thenReturn(List.of(
				VocabularyTopic.builder().id(1L).code("travel").name("Travel").level("B1").build()));
		when(topicMapper.findMasterySummaryByUserId("user-1")).thenReturn(List.of(
				TopicMasterySummaryRow.builder().topicId(1L).wordCount(20).masteredCount(4).build()));

		List<TopicSummaryDto> result = service.listTopics("user-1");

		assertThat(result).hasSize(1);
		assertThat(result.get(0).getTopicId()).isEqualTo(1L);
		assertThat(result.get(0).getWordCount()).isEqualTo(20);
		assertThat(result.get(0).getMasteredCount()).isEqualTo(4);
	}

	@Test
	void listTopicsDefaultsToZeroCountsForATopicWithNoMasterySummaryRow() {
		when(topicMapper.findAll()).thenReturn(List.of(VocabularyTopic.builder().id(1L).code("travel").name("Travel").build()));
		when(topicMapper.findMasterySummaryByUserId("user-1")).thenReturn(List.of());

		List<TopicSummaryDto> result = service.listTopics("user-1");

		assertThat(result.get(0).getWordCount()).isZero();
		assertThat(result.get(0).getMasteredCount()).isZero();
	}

	@Test
	void startSectionTopsUpTheTopicWhenItHasFewerWordsThanTheRequestedSectionSize() {
		when(topicMapper.findById(1L)).thenReturn(VocabularyTopic.builder().id(1L).code("travel").name("Travel").build());
		when(libraryWordMapper.countByTopicId(1L)).thenReturn(2);
		when(libraryWordMapper.findWordsByTopicId(1L)).thenReturn(List.of("passport", "luggage"));
		when(libraryWordGenerator.generate(eq("Travel"), eq(List.of("passport", "luggage")), anyInt()))
				.thenReturn(List.of(new GeneratedLibraryWord("itinerary", "NOUN", "lịch trình", "She planned a detailed itinerary for the trip.")));
		when(ttsClient.synthesize(any())).thenReturn(TtsAudio.builder().audioBytes(new byte[]{1, 2, 3}).mimeType("audio/wav").build());
		VocabularyLibraryWord itinerary = VocabularyLibraryWord.builder().id(10L).topicId(1L).word("itinerary").wordType(VocabularyType.NOUN)
				.meaningVi("lịch trình").exampleEn("She planned a detailed itinerary for the trip.").build();
		when(libraryWordMapper.findNotYetMasteredByTopicId(eq(1L), eq("user-1"), anyInt())).thenReturn(List.of(itinerary));
		when(libraryWordMapper.findById(10L)).thenReturn(itinerary);

		service.startSection("user-1", 1L, new StartSectionRequest());

		verify(libraryWordGenerator, times(1)).generate(eq("Travel"), eq(List.of("passport", "luggage")), anyInt());
		verify(libraryWordMapper, times(1)).insert(any());
		verify(sectionMapper, times(1)).insertAttempt(any());
	}

	@Test
	void startSectionSkipsTopUpWhenTheTopicAlreadyHasEnoughWords() {
		when(topicMapper.findById(1L)).thenReturn(VocabularyTopic.builder().id(1L).code("travel").name("Travel").build());
		when(libraryWordMapper.countByTopicId(1L)).thenReturn(50);
		VocabularyLibraryWord itinerary = VocabularyLibraryWord.builder().id(10L).topicId(1L).word("itinerary").wordType(VocabularyType.NOUN)
				.meaningVi("lịch trình").exampleEn("An itinerary.").build();
		when(libraryWordMapper.findNotYetMasteredByTopicId(eq(1L), eq("user-1"), anyInt())).thenReturn(List.of(itinerary));
		when(libraryWordMapper.findById(10L)).thenReturn(itinerary);

		service.startSection("user-1", 1L, new StartSectionRequest());

		verify(libraryWordGenerator, never()).generate(any(), any(), anyInt());
	}

	@Test
	void startSectionFillsRemainderWithRandomWordsWhenNotEnoughUnmasteredWordsExist() {
		when(topicMapper.findById(1L)).thenReturn(VocabularyTopic.builder().id(1L).code("travel").name("Travel").build());
		when(libraryWordMapper.countByTopicId(1L)).thenReturn(50);
		VocabularyLibraryWord itinerary = VocabularyLibraryWord.builder().id(10L).topicId(1L).word("itinerary").wordType(VocabularyType.NOUN)
				.meaningVi("lịch trình").exampleEn("An itinerary.").build();
		VocabularyLibraryWord visa = VocabularyLibraryWord.builder().id(11L).topicId(1L).word("visa").wordType(VocabularyType.NOUN)
				.meaningVi("thị thực").exampleEn("A visa.").build();
		when(libraryWordMapper.findNotYetMasteredByTopicId(eq(1L), eq("user-1"), anyInt())).thenReturn(List.of(itinerary));
		when(libraryWordMapper.findRandomByTopicIdExcluding(eq(1L), eq(List.of(10L)), anyInt())).thenReturn(List.of(visa));
		when(libraryWordMapper.findById(10L)).thenReturn(itinerary);
		when(libraryWordMapper.findById(11L)).thenReturn(visa);

		StartSectionRequest request = new StartSectionRequest();
		request.setSectionSize(2);
		service.startSection("user-1", 1L, request);

		ArgumentCaptor<com.remelearning.english.vocabulary.library.domain.VocabularySectionAttempt> captor =
				ArgumentCaptor.forClass(com.remelearning.english.vocabulary.library.domain.VocabularySectionAttempt.class);
		verify(sectionMapper).insertAttempt(captor.capture());
		assertThat(captor.getValue().getSectionSize()).isEqualTo(2);
	}

	@Test
	void startSectionThrowsNotFoundForAnUnknownTopic() {
		when(topicMapper.findById(99L)).thenReturn(null);

		assertThatThrownBy(() -> service.startSection("user-1", 99L, new StartSectionRequest()))
				.isInstanceOf(BusinessException.class);
	}
}
