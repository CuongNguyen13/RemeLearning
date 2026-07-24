package com.remelearning.english.speaking.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.remelearning.common.ai.pronunciation.PhonemePronunciationScore;
import com.remelearning.common.ai.pronunciation.PronunciationScore;
import com.remelearning.common.ai.pronunciation.PronunciationScoringClient;
import com.remelearning.common.ai.pronunciation.WordPronunciationScore;
import com.remelearning.common.ai.tts.TtsAudio;
import com.remelearning.common.ai.tts.TtsClient;
import com.remelearning.common.exception.BusinessException;
import com.remelearning.common.storage.StorageClient;
import com.remelearning.english.practice.dto.PracticeRedoRequest;
import com.remelearning.english.practice.service.PracticeService;
import com.remelearning.english.pronunciation.domain.PronunciationType;
import com.remelearning.english.pronunciation.domain.PronunciationWeakPoint;
import com.remelearning.english.pronunciation.service.PronunciationWeakPointService;
import com.remelearning.english.speaking.domain.SpeakingAttemptDetailRow;
import com.remelearning.english.speaking.domain.SpeakingPracticeItem;
import com.remelearning.english.speaking.dto.GenerateSpeakingPracticeRequest;
import com.remelearning.english.speaking.dto.SpeakingAttemptDetailDto;
import com.remelearning.english.speaking.dto.SpeakingAttemptResultDto;
import com.remelearning.english.speaking.dto.SpeakingPracticeItemDto;
import com.remelearning.english.speaking.generator.GeneratedSpeakingPractice;
import com.remelearning.english.speaking.generator.SpeakingPracticeGenerator;
import com.remelearning.english.speaking.mapper.SpeakingMapper;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mock.web.MockMultipartFile;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SpeakingLearnServiceImplTest {

	private final SpeakingMapper mapper = mock(SpeakingMapper.class);
	private final SpeakingPracticeGenerator generator = mock(SpeakingPracticeGenerator.class);
	private final PronunciationWeakPointService weakPointService = mock(PronunciationWeakPointService.class);
	private final PronunciationScoringClient scoringClient = mock(PronunciationScoringClient.class);
	private final TtsClient ttsClient = mock(TtsClient.class);
	private final StorageClient storageClient = mock(StorageClient.class);
	private final PracticeService practiceService = mock(PracticeService.class);
	private final ObjectMapper objectMapper = new ObjectMapper();
	private final SpeakingLearnServiceImpl service = new SpeakingLearnServiceImpl(
			mapper, generator, weakPointService, scoringClient, ttsClient, storageClient, practiceService, objectMapper, "F1", "en");

	@Test
	void generateSynthesizesSampleAudioAndFallsBackToTopWeakPoints() {
		when(weakPointService.getWeakPoints("user-1", null)).thenReturn(List.of(
				PronunciationWeakPoint.builder().label("th sound").pronunciationType(PronunciationType.OTHER).forgettingScore(0.8).build()));
		when(generator.generate(eq(List.of("th sound")), any(), any()))
				.thenReturn(new GeneratedSpeakingPractice("Practice", "Think about the weather.", "Hay nghi ve thoi tiet."));
		when(ttsClient.synthesize(any())).thenReturn(TtsAudio.builder().audioBytes("wav-bytes".getBytes()).build());
		simulateGeneratedItemId(7L);

		SpeakingPracticeItemDto dto = service.generate("user-1", new GenerateSpeakingPracticeRequest());

		assertThat(dto.getPracticeItemId()).isEqualTo(7L);
		assertThat(dto.getTargetText()).isEqualTo("Think about the weather.");
		assertThat(dto.getSampleAudioUrl()).isEqualTo("/api/v1/learners/user-1/learn/speaking/items/7/sample-audio");
		verify(storageClient).write(eq("speaking/sample/user-1/7.wav"), any(), eq(9L));
	}

	@Test
	void submitScoresViaAiServiceAndFeedsWeakPointPipeline() throws Exception {
		SpeakingPracticeItem item = SpeakingPracticeItem.builder()
				.id(10L).userId("user-1").targetText("Think about the weather.").build();
		when(mapper.findItemById(10L)).thenReturn(item);
		when(storageClient.read(anyString())).thenReturn(new java.io.ByteArrayInputStream("wav".getBytes()));
		when(scoringClient.score(any(), anyString(), eq("Think about the weather."), eq("en"))).thenReturn(
				new PronunciationScore(0.72, List.of(
						new WordPronunciationScore("Think", 0.3, List.of(new PhonemePronunciationScore("θ", 0.2))),
						new WordPronunciationScore("weather", 0.9, List.of(new PhonemePronunciationScore("ð", 0.9)))),
						"Sink about the weather.", List.of("θ")));

		MockMultipartFile audio = new MockMultipartFile("audio", "attempt.wav", "audio/wav", "fake-audio".getBytes());

		SpeakingAttemptResultDto result = service.submit("user-1", 10L, audio);

		assertThat(result.getOverall()).isEqualTo(0.72);
		assertThat(result.getWeakPhonemes()).containsExactly("θ");
		assertThat(result.getActionAdvice()).anyMatch(a -> a.contains("Think"));

		ArgumentCaptor<PracticeRedoRequest> redoCaptor = ArgumentCaptor.forClass(PracticeRedoRequest.class);
		verify(practiceService).redo(redoCaptor.capture());
		assertThat(redoCaptor.getValue().getAttempts()).hasSize(2);
		assertThat(redoCaptor.getValue().getAttempts().get(0).getLabel()).isEqualTo("Think");
		assertThat(redoCaptor.getValue().getAttempts().get(0).isCorrect()).isFalse();
		assertThat(redoCaptor.getValue().getAttempts().get(1).isCorrect()).isTrue();
		assertThat(redoCaptor.getValue().getAttempts().get(0).getCategory()).isEqualTo("pronunciation");
	}

	@Test
	void getItemThrowsNotFoundForUnknownId() {
		when(mapper.findItemById(99L)).thenReturn(null);

		assertThatThrownBy(() -> service.getItem(99L)).isInstanceOf(BusinessException.class);
	}

	@Test
	void getAttemptDetailReadsPersistedScoresWithoutReScoring() {
		SpeakingAttemptDetailRow row = SpeakingAttemptDetailRow.builder()
				.attemptId(20L).level("B1").examType("TOEIC").topic("Practice").targetText("Think about the weather.")
				.overallScore(0.9)
				.wordScoresJson("[]")
				.transcript("Think about the weather.")
				.weakPhonemesJson("[]")
				.createdAt(Instant.now())
				.build();
		when(mapper.findAttemptDetailByIdAndUserId(20L, "user-1")).thenReturn(row);

		SpeakingAttemptDetailDto dto = service.getAttemptDetail("user-1", 20L);

		assertThat(dto.getOverallScore()).isEqualTo(0.9);
		verify(scoringClient, org.mockito.Mockito.never()).score(any(), any(), any(), any());
	}

	@Test
	void generatePracticeFromAttemptTargetsThePastAttemptsWeakPhonemes() {
		SpeakingAttemptDetailRow attempt = SpeakingAttemptDetailRow.builder()
				.attemptId(20L).level("B1").examType("TOEIC").topic("Practice").targetText("Think about the weather.")
				.overallScore(0.72).wordScoresJson("[]").transcript("Sink about the weather.")
				.weakPhonemesJson("[\"θ\", \"ð\"]").createdAt(Instant.now()).build();
		when(mapper.findAttemptDetailByIdAndUserId(20L, "user-1")).thenReturn(attempt);
		when(generator.generate(eq(List.of("θ", "ð")), eq("B1"), eq("TOEIC")))
				.thenReturn(new GeneratedSpeakingPractice("Practice", "The path to the north is smooth.", "Con duong den phia bac rat bang phang."));
		when(ttsClient.synthesize(any())).thenReturn(TtsAudio.builder().audioBytes("wav-bytes".getBytes()).build());
		simulateGeneratedItemId(8L);
		when(mapper.findItemsByUserId("user-1")).thenReturn(List.of(
				SpeakingPracticeItem.builder().id(8L).userId("user-1").targetText("The path to the north is smooth.").build()));

		List<SpeakingPracticeItemDto> items = service.generatePracticeFromAttempt("user-1", 20L);

		assertThat(items).hasSize(1);
		assertThat(items.get(0).getTargetText()).isEqualTo("The path to the north is smooth.");
		verify(generator).generate(List.of("θ", "ð"), "B1", "TOEIC");
	}

	@Test
	void generatePracticeFromAttemptThrowsNotFoundWhenAttemptDoesNotBelongToLearner() {
		when(mapper.findAttemptDetailByIdAndUserId(99L, "user-1")).thenReturn(null);

		assertThatThrownBy(() -> service.generatePracticeFromAttempt("user-1", 99L))
				.isInstanceOf(BusinessException.class);
	}

	private void simulateGeneratedItemId(Long id) {
		org.mockito.Mockito.doAnswer(invocation -> {
			SpeakingPracticeItem item = invocation.getArgument(0);
			item.setId(id);
			return null;
		}).when(mapper).insertItem(any(SpeakingPracticeItem.class));
	}
}
