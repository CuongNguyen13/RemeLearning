package com.remelearning.english.dictation.service;

import com.remelearning.common.ai.tts.TtsAudio;
import com.remelearning.common.ai.tts.TtsClient;
import com.remelearning.common.ai.tts.TtsRequest;
import com.remelearning.common.event.WeakPointPayload;
import com.remelearning.common.exception.BusinessException;
import com.remelearning.common.storage.StorageClient;
import com.remelearning.english.dictation.analyzer.DictationAnalysis;
import com.remelearning.english.dictation.analyzer.DictationAnalyzer;
import com.remelearning.english.dictation.domain.DictationAttempt;
import com.remelearning.english.dictation.domain.DictationClip;
import com.remelearning.english.dictation.domain.DictationMiss;
import com.remelearning.english.dictation.domain.DictationPracticeItem;
import com.remelearning.english.dictation.domain.MissWordCount;
import com.remelearning.english.dictation.dto.DictationAttemptRequest;
import com.remelearning.english.dictation.dto.DictationAttemptResultDto;
import com.remelearning.english.dictation.dto.DictationClipDto;
import com.remelearning.english.dictation.dto.DictationPracticeItemDto;
import com.remelearning.english.dictation.dto.StartDictationSessionRequest;
import com.remelearning.english.dictation.kafka.DictationGapEventPublisher;
import com.remelearning.english.dictation.mapper.DictationMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DictationServiceImplTest {

	private final DictationMapper dictationMapper = mock(DictationMapper.class);
	private final DictationAnalyzer dictationAnalyzer = mock(DictationAnalyzer.class);
	private final DictationGapEventPublisher gapEventPublisher = mock(DictationGapEventPublisher.class);
	private final TtsClient ttsClient = mock(TtsClient.class);
	private final StorageClient storageClient = mock(StorageClient.class);
	private DictationServiceImpl service;

	@BeforeEach
	void setUp() {
		service = new DictationServiceImpl(dictationMapper, dictationAnalyzer, gapEventPublisher,
				ttsClient, storageClient, "F1", "en", 8);
	}

	@Test
	void startSessionMapsRandomClipsToDtosWithAudioUrls() {
		when(dictationMapper.findRandomClipsByFacets("Listening", "B1", null, "TOEIC", 3)).thenReturn(List.of(
				DictationClip.builder().id(7L).code("toeic-0001").title("Photocopy").skill("Listening")
						.level("B1").examType("TOEIC").storageKey("toeic/toeic-0001.mp3").build()));

		StartDictationSessionRequest request = new StartDictationSessionRequest();
		request.setSkill("Listening");
		request.setLevel("B1");
		request.setExamType("TOEIC");
		request.setCount(3);

		List<DictationClipDto> clips = service.startSession("user-1", request);

		assertThat(clips).hasSize(1);
		assertThat(clips.get(0).getClipId()).isEqualTo(7L);
		assertThat(clips.get(0).getAudioUrl()).isEqualTo("/api/v1/dictation/clips/7/audio");
	}

	@Test
	void submitAttemptGradesRecordsMissesAndPublishesWeakPoints() {
		when(dictationMapper.findClipById(42L)).thenReturn(DictationClip.builder()
				.id(42L).code("c-1").scriptText("She was reluctant to admit it.").build());
		simulateGeneratedAttemptId(500L);
		when(dictationAnalyzer.analyzeAttempt(anyString(), anyList())).thenReturn(DictationAnalysis.builder()
				.suggestions(List.of("Nghe lại từ 'reluctant'."))
				.practiceSentences(List.of("She was reluctant to leave."))
				.build());
		when(dictationMapper.countMissesForWord(eq("user-1"), anyString())).thenReturn(1);

		DictationAttemptRequest request = new DictationAttemptRequest();
		request.setUserId("user-1");
		request.setClipId(42L);
		request.setUserTranscript("She was to admit it.");

		DictationAttemptResultDto result = service.submitAttempt(request);

		// "reluctant" was omitted -> one miss, accuracy below 1.
		assertThat(result.getReferenceText()).isEqualTo("She was reluctant to admit it.");
		assertThat(result.getAccuracy()).isLessThan(1.0);
		assertThat(result.getAiSuggestions()).containsExactly("Nghe lại từ 'reluctant'.");

		ArgumentCaptor<List<DictationMiss>> missCaptor = ArgumentCaptor.forClass(List.class);
		verify(dictationMapper).insertMisses(missCaptor.capture());
		assertThat(missCaptor.getValue()).extracting(DictationMiss::getExpectedWord).contains("reluctant");

		// The AI-suggested practice sentence is persisted, and misses are published to the pipeline.
		verify(dictationMapper).insertPracticeItem(any(DictationPracticeItem.class));
		ArgumentCaptor<List<WeakPointPayload>> weakCaptor = ArgumentCaptor.forClass(List.class);
		verify(gapEventPublisher).publish(eq("dictation-clip-42"), eq("user-1"), weakCaptor.capture());
		assertThat(weakCaptor.getValue()).extracting(WeakPointPayload::getLabel).contains("reluctant");
		assertThat(weakCaptor.getValue().get(0).getCategory()).isEqualTo("vocabulary");
	}

	@Test
	void submitAttemptRejectsRequestWithNeitherClipNorPracticeItem() {
		DictationAttemptRequest request = new DictationAttemptRequest();
		request.setUserId("user-1");
		request.setUserTranscript("anything");

		assertThatThrownBy(() -> service.submitAttempt(request)).isInstanceOf(BusinessException.class);
	}

	@Test
	void submitAttemptThrowsNotFoundForUnknownClip() {
		when(dictationMapper.findClipById(99L)).thenReturn(null);

		DictationAttemptRequest request = new DictationAttemptRequest();
		request.setUserId("user-1");
		request.setClipId(99L);
		request.setUserTranscript("anything");

		assertThatThrownBy(() -> service.submitAttempt(request)).isInstanceOf(BusinessException.class);
	}

	@Test
	void generateAiPracticeSynthesizesPendingItemsAndStoresAudio() {
		when(dictationMapper.findPracticeItemsWithoutAudio("user-1")).thenReturn(List.of(
				DictationPracticeItem.builder().id(11L).userId("user-1").sentenceText("Listen and write.").build()));
		when(ttsClient.synthesize(any(TtsRequest.class))).thenReturn(
				TtsAudio.builder().audioBytes(new byte[] { 1, 2, 3 }).mimeType("audio/wav").sampleRate(44100).build());
		when(dictationMapper.findPracticeItemsByUserId("user-1")).thenReturn(List.of(
				DictationPracticeItem.builder().id(11L).userId("user-1").sentenceText("Listen and write.")
						.storageKey("generated/user-1/11.wav").build()));

		List<DictationPracticeItemDto> items = service.generateAiPractice("user-1");

		verify(storageClient).write(eq("generated/user-1/11.wav"), any(), eq(3L));
		verify(dictationMapper).updatePracticeItemStorageKey(11L, "generated/user-1/11.wav");
		assertThat(items).hasSize(1);
		assertThat(items.get(0).getAudioUrl()).isEqualTo("/api/v1/dictation/ai-practice/items/11/audio");
	}

	@Test
	void generateAiPracticeCreatesFreshItemsWhenNonePending() {
		when(dictationMapper.findPracticeItemsWithoutAudio("user-1")).thenReturn(List.of());
		when(dictationMapper.findTopMissedWords("user-1", 8)).thenReturn(List.of(
				MissWordCount.builder().word("reluctant").missCount(3).build()));
		when(dictationAnalyzer.generatePracticeSentences(List.of("reluctant")))
				.thenReturn(List.of("She was reluctant to leave."));
		simulateGeneratedPracticeItemId(21L);
		when(ttsClient.synthesize(any(TtsRequest.class))).thenReturn(
				TtsAudio.builder().audioBytes(new byte[] { 9 }).mimeType("audio/wav").build());
		when(dictationMapper.findPracticeItemsByUserId("user-1")).thenReturn(List.of());

		service.generateAiPractice("user-1");

		verify(dictationMapper).insertPracticeItem(any(DictationPracticeItem.class));
		verify(storageClient).write(eq("generated/user-1/21.wav"), any(), eq(1L));
	}

	// Simulates MyBatis useGeneratedKeys writing the id back onto the inserted attempt.
	private void simulateGeneratedAttemptId(long id) {
		doAnswer(invocation -> {
			DictationAttempt attempt = invocation.getArgument(0);
			attempt.setId(id);
			return null;
		}).when(dictationMapper).insertAttempt(any(DictationAttempt.class));
	}

	// Simulates the generated id being written back onto an inserted practice item.
	private void simulateGeneratedPracticeItemId(long id) {
		doAnswer(invocation -> {
			DictationPracticeItem item = invocation.getArgument(0);
			item.setId(id);
			return null;
		}).when(dictationMapper).insertPracticeItem(any(DictationPracticeItem.class));
	}
}
