package com.remelearning.english.dictation.service;

import com.remelearning.common.ai.align.SentenceAlignmentClient;
import com.remelearning.common.ai.align.SentenceTiming;
import com.remelearning.common.ai.tts.TtsAudio;
import com.remelearning.common.ai.tts.TtsClient;
import com.remelearning.common.ai.tts.TtsRequest;
import com.remelearning.common.event.WeakPointPayload;
import com.remelearning.common.exception.BusinessException;
import com.remelearning.common.storage.StorageClient;
import com.remelearning.english.dictation.analyzer.DialogueGenerationResult;
import com.remelearning.english.dictation.analyzer.DictationAnalysis;
import com.remelearning.english.dictation.analyzer.DictationAnalyzer;
import com.remelearning.english.dictation.analyzer.DictationDialogueGenerator;
import com.remelearning.english.dictation.analyzer.DictationDialogueLine;
import com.remelearning.english.dictation.analyzer.DictationErrorCategory;
import com.remelearning.english.dictation.analyzer.DictationErrorEntry;
import com.remelearning.english.dictation.analyzer.DictationSentenceTranslator;
import com.remelearning.english.dictation.domain.DictationAttempt;
import com.remelearning.english.dictation.domain.DictationAttemptDetailRow;
import com.remelearning.english.dictation.domain.DictationClip;
import com.remelearning.english.dictation.domain.DictationClipSentence;
import com.remelearning.english.dictation.domain.DictationLessonRow;
import com.remelearning.english.dictation.domain.DictationMiss;
import com.remelearning.english.dictation.domain.DictationPracticeItem;
import com.remelearning.english.dictation.domain.FolderCount;
import com.remelearning.english.dictation.domain.MissWordCount;
import com.remelearning.english.dictation.dto.DictationAttemptDetailDto;
import com.remelearning.english.dictation.dto.DictationAttemptRequest;
import com.remelearning.english.dictation.dto.DictationAttemptResultDto;
import com.remelearning.english.dictation.dto.DictationClipDetailDto;
import com.remelearning.english.dictation.dto.DictationClipDto;
import com.remelearning.english.dictation.dto.DictationFacetsDto;
import com.remelearning.english.dictation.dto.DictationFolderDto;
import com.remelearning.english.dictation.dto.DictationLessonSummaryDto;
import com.remelearning.english.dictation.dto.DictationHistoryEntryDto;
import com.remelearning.english.dictation.dto.DictationPracticeItemDetailDto;
import com.remelearning.english.dictation.dto.DictationPracticeItemDto;
import com.remelearning.english.dictation.dto.DictationPracticeType;
import com.remelearning.english.dictation.dto.DictationSentenceMistakeRequest;
import com.remelearning.english.dictation.domain.DictationHistoryRow;
import com.remelearning.english.dictation.dto.GenerateAiPracticeRequest;
import com.remelearning.english.dictation.dto.StartDictationSessionRequest;
import com.remelearning.english.dictation.dto.WordDiffTag;
import com.remelearning.english.dictation.kafka.DictationGapEventPublisher;
import com.remelearning.english.dictation.mapper.DictationMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class DictationServiceImplTest {

	private final DictationMapper dictationMapper = mock(DictationMapper.class);
	private final DictationAnalyzer dictationAnalyzer = mock(DictationAnalyzer.class);
	private final DictationDialogueGenerator dialogueGenerator = mock(DictationDialogueGenerator.class);
	private final DictationSentenceTranslator sentenceTranslator = mock(DictationSentenceTranslator.class);
	private final DictationGapEventPublisher gapEventPublisher = mock(DictationGapEventPublisher.class);
	private final TtsClient ttsClient = mock(TtsClient.class);
	private final StorageClient storageClient = mock(StorageClient.class);
	private final SentenceAlignmentClient sentenceAlignmentClient = mock(SentenceAlignmentClient.class);
	private final com.fasterxml.jackson.databind.ObjectMapper objectMapper = new com.fasterxml.jackson.databind.ObjectMapper();
	private DictationServiceImpl service;

	@BeforeEach
	void setUp() {
		service = new DictationServiceImpl(dictationMapper, dictationAnalyzer, dialogueGenerator, sentenceTranslator,
				gapEventPublisher, ttsClient, storageClient, sentenceAlignmentClient, objectMapper, "F1", "en", 8, 3);
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
		when(dictationAnalyzer.analyzeAttempt(anyString(), anyString(), anyList())).thenReturn(DictationAnalysis.builder()
				.errorTable(List.of())
				.rootCauses(List.of())
				.actionAdvice(List.of("Nghe lại từ 'reluctant'."))
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
		assertThat(result.getActionAdvice()).containsExactly("Nghe lại từ 'reluctant'.");

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
	void submitAttemptRoutesEachMissedWordToItsAnalyzedErrorCategory() {
		when(dictationMapper.findClipById(42L)).thenReturn(DictationClip.builder()
				.id(42L).code("c-1").scriptText("She was reluctant to admit it.").build());
		simulateGeneratedAttemptId(502L);
		when(dictationAnalyzer.analyzeAttempt(anyString(), anyString(), anyList())).thenReturn(DictationAnalysis.builder()
				.errorTable(List.of(
						DictationErrorEntry.builder().original("reluctant").transcribed("").category(DictationErrorCategory.PHONOLOGY).build()))
				.rootCauses(List.of())
				.actionAdvice(List.of())
				.practiceSentences(List.of())
				.build());
		when(dictationMapper.countMissesForWord(eq("user-1"), anyString())).thenReturn(1);

		DictationAttemptRequest request = new DictationAttemptRequest();
		request.setUserId("user-1");
		request.setClipId(42L);
		request.setUserTranscript("She was to admit it.");

		service.submitAttempt(request);

		ArgumentCaptor<List<WeakPointPayload>> weakCaptor = ArgumentCaptor.forClass(List.class);
		verify(gapEventPublisher).publish(eq("dictation-clip-42"), eq("user-1"), weakCaptor.capture());
		assertThat(weakCaptor.getValue()).extracting(WeakPointPayload::getCategory).containsExactly("pronunciation");
	}

	@Test
	void submitAttemptFoldsSentenceModeMistakesIntoMissesAndWeakPoints() {
		when(dictationMapper.findClipById(42L)).thenReturn(DictationClip.builder()
				.id(42L).code("c-1").scriptText("She was reluctant to admit it.").build());
		simulateGeneratedAttemptId(501L);
		when(dictationAnalyzer.analyzeAttempt(anyString(), anyString(), anyList())).thenReturn(DictationAnalysis.builder()
				.errorTable(List.of()).rootCauses(List.of()).actionAdvice(List.of()).practiceSentences(List.of()).build());
		when(dictationMapper.countMissesForWord(eq("user-1"), anyString())).thenReturn(1);

		DictationSentenceMistakeRequest mistake = new DictationSentenceMistakeRequest();
		mistake.setSentenceIndex(0);
		mistake.setExpectedText("She was reluctant to admit it.");
		mistake.setAttemptedText("She was reluctan to admit it.");

		DictationAttemptRequest request = new DictationAttemptRequest();
		request.setUserId("user-1");
		request.setClipId(42L);
		// The learner eventually retyped the sentence correctly, so the final transcript is a perfect
		// match - only the recorded sentenceMistakes carries evidence of the earlier wrong attempt.
		request.setUserTranscript("She was reluctant to admit it.");
		request.setSentenceMistakes(List.of(mistake));

		DictationAttemptResultDto result = service.submitAttempt(request);

		assertThat(result.getAccuracy()).isEqualTo(1.0);

		ArgumentCaptor<List<DictationMiss>> missCaptor = ArgumentCaptor.forClass(List.class);
		verify(dictationMapper).insertMisses(missCaptor.capture());
		assertThat(missCaptor.getValue()).extracting(DictationMiss::getExpectedWord).contains("reluctant");

		ArgumentCaptor<List<WeakPointPayload>> weakCaptor = ArgumentCaptor.forClass(List.class);
		verify(gapEventPublisher).publish(eq("dictation-clip-42"), eq("user-1"), weakCaptor.capture());
		assertThat(weakCaptor.getValue()).extracting(WeakPointPayload::getLabel).contains("reluctant");
	}

	@Test
	void submitAttemptSkipsSentenceMistakeProcessingWhenNoneRecorded() {
		when(dictationMapper.findClipById(42L)).thenReturn(DictationClip.builder()
				.id(42L).code("c-1").scriptText("She was reluctant to admit it.").build());
		simulateGeneratedAttemptId(502L);
		when(dictationAnalyzer.analyzeAttempt(anyString(), anyString(), anyList())).thenReturn(DictationAnalysis.builder()
				.errorTable(List.of()).rootCauses(List.of()).actionAdvice(List.of()).practiceSentences(List.of()).build());

		DictationAttemptRequest request = new DictationAttemptRequest();
		request.setUserId("user-1");
		request.setClipId(42L);
		request.setUserTranscript("She was reluctant to admit it.");

		DictationAttemptResultDto result = service.submitAttempt(request);

		assertThat(result.getAccuracy()).isEqualTo(1.0);
		verify(dictationMapper, org.mockito.Mockito.never()).insertMisses(anyList());
	}

	@Test
	void getFacetsIncludesConfiguredMinListensForHint() {
		when(dictationMapper.findDistinctSkills()).thenReturn(List.of("Listening"));
		when(dictationMapper.findDistinctLevels()).thenReturn(List.of());
		when(dictationMapper.findDistinctTopics()).thenReturn(List.of());
		when(dictationMapper.findDistinctExamTypes()).thenReturn(List.of());

		DictationFacetsDto facets = service.getFacets();

		assertThat(facets.getMinListensForHint()).isEqualTo(3);
	}

	@Test
	void listFoldersMapsFolderCountsToDtos() {
		when(dictationMapper.findDistinctFolders()).thenReturn(List.of(
				FolderCount.builder().folder("english-conversations").count(42).build()));

		List<DictationFolderDto> folders = service.listFolders();

		assertThat(folders).hasSize(1);
		assertThat(folders.get(0).getFolderId()).isEqualTo("english-conversations");
		assertThat(folders.get(0).getName()).isEqualTo("english-conversations");
		assertThat(folders.get(0).getLessonCount()).isEqualTo(42);
	}

	@Test
	void listFolderLessonsMapsRowsWithLearnerProgress() {
		when(dictationMapper.findLessonSummariesByFolder("english-conversations", "user-1")).thenReturn(List.of(
				DictationLessonRow.builder().clipId(5L).code("c-1").title("At home").level("A1")
						.sentenceCount(6).attemptCount(2).latestAccuracy(0.75).build()));

		List<DictationLessonSummaryDto> lessons = service.listFolderLessons("english-conversations", "user-1");

		assertThat(lessons).hasSize(1);
		assertThat(lessons.get(0).getClipId()).isEqualTo(5L);
		assertThat(lessons.get(0).getAudioUrl()).isEqualTo("/api/v1/dictation/clips/5/audio");
		assertThat(lessons.get(0).getLevel()).isEqualTo("A1");
		assertThat(lessons.get(0).getSentenceCount()).isEqualTo(6);
		assertThat(lessons.get(0).getAttemptCount()).isEqualTo(2);
		assertThat(lessons.get(0).getLatestAccuracy()).isEqualTo(0.75);
	}

	@Test
	void getClipDetailReturnsScriptAndOrderedSentences() {
		when(dictationMapper.findClipById(5L)).thenReturn(
				DictationClip.builder().id(5L).code("c-1").title("At home").scriptText("Hi.\nBye.").build());
		when(dictationMapper.findSentencesByClipId(5L)).thenReturn(List.of(
				DictationClipSentence.builder().clipId(5L).seq(1).text("Hi.").startMs(0).endMs(500).build(),
				DictationClipSentence.builder().clipId(5L).seq(2).text("Bye.").build()));

		DictationClipDetailDto detail = service.getClipDetail(5L, null);

		assertThat(detail.getScriptText()).isEqualTo("Hi.\nBye.");
		assertThat(detail.getSentences()).hasSize(2);
		assertThat(detail.getSentences().get(0).getStartMs()).isEqualTo(0);
		assertThat(detail.getSentences().get(1).getStartMs()).isNull();
	}

	@Test
	void getClipDetailAlignsMissingTimestampsAndPersistsThem() {
		when(dictationMapper.findClipById(5L)).thenReturn(
				DictationClip.builder().id(5L).code("c-1").title("At home").scriptText("Hi.\nBye.")
						.storageKey("english-conversations/c-1.mp3").build());
		when(dictationMapper.findSentencesByClipId(5L)).thenReturn(List.of(
				DictationClipSentence.builder().clipId(5L).seq(1).text("Hi.").build(),
				DictationClipSentence.builder().clipId(5L).seq(2).text("Bye.").build()));
		InputStream audio = new ByteArrayInputStream(new byte[] { 1, 2, 3 });
		when(storageClient.read("english-conversations/c-1.mp3")).thenReturn(audio);
		when(sentenceAlignmentClient.align(eq(audio), eq("english-conversations/c-1.mp3"), eq(List.of("Hi.", "Bye."))))
				.thenReturn(List.of(new SentenceTiming(0, 500), new SentenceTiming(500, 900)));

		DictationClipDetailDto detail = service.getClipDetail(5L, null);

		assertThat(detail.getSentences().get(0).getStartMs()).isEqualTo(0);
		assertThat(detail.getSentences().get(0).getEndMs()).isEqualTo(500);
		assertThat(detail.getSentences().get(1).getStartMs()).isEqualTo(500);
		assertThat(detail.getSentences().get(1).getEndMs()).isEqualTo(900);
		verify(dictationMapper).updateSentenceTimestamps(5L, 1, 0, 500);
		verify(dictationMapper).updateSentenceTimestamps(5L, 2, 500, 900);
	}

	@Test
	void getClipDetailSkipsAlignmentWhenAllSentencesAlreadyTimestamped() {
		when(dictationMapper.findClipById(5L)).thenReturn(
				DictationClip.builder().id(5L).code("c-1").storageKey("c-1.mp3").build());
		when(dictationMapper.findSentencesByClipId(5L)).thenReturn(List.of(
				DictationClipSentence.builder().clipId(5L).seq(1).text("Hi.").startMs(0).endMs(500).build()));

		service.getClipDetail(5L, null);

		verifyNoInteractions(sentenceAlignmentClient);
	}

	@Test
	void getClipDetailSkipsAlignmentWhenClipHasNoStorageKey() {
		when(dictationMapper.findClipById(5L)).thenReturn(
				DictationClip.builder().id(5L).code("c-1").build());
		when(dictationMapper.findSentencesByClipId(5L)).thenReturn(List.of(
				DictationClipSentence.builder().clipId(5L).seq(1).text("Hi.").build()));

		DictationClipDetailDto detail = service.getClipDetail(5L, null);

		verifyNoInteractions(sentenceAlignmentClient);
		assertThat(detail.getSentences().get(0).getStartMs()).isNull();
	}

	@Test
	void getClipDetailDegradesGracefullyWhenAlignmentFails() {
		when(dictationMapper.findClipById(5L)).thenReturn(
				DictationClip.builder().id(5L).code("c-1").storageKey("c-1.mp3").build());
		when(dictationMapper.findSentencesByClipId(5L)).thenReturn(List.of(
				DictationClipSentence.builder().clipId(5L).seq(1).text("Hi.").build()));
		when(storageClient.read("c-1.mp3")).thenReturn(new ByteArrayInputStream(new byte[] { 1 }));
		when(sentenceAlignmentClient.align(any(), anyString(), anyList()))
				.thenThrow(new IllegalStateException("ai-service unreachable"));

		DictationClipDetailDto detail = service.getClipDetail(5L, null);

		assertThat(detail.getSentences().get(0).getStartMs()).isNull();
	}

	@Test
	void getClipDetailThrowsNotFoundForUnknownClip() {
		when(dictationMapper.findClipById(99L)).thenReturn(null);

		assertThatThrownBy(() -> service.getClipDetail(99L, null)).isInstanceOf(BusinessException.class);
	}

	@Test
	void getClipDetailLazilyTranslatesMissingSentencesWhenVietnameseRequested() {
		when(dictationMapper.findClipById(5L)).thenReturn(DictationClip.builder().id(5L).code("c-1").build());
		when(dictationMapper.findSentencesByClipId(5L)).thenReturn(List.of(
				DictationClipSentence.builder().clipId(5L).seq(1).text("Hi.").startMs(0).endMs(500).build(),
				DictationClipSentence.builder().clipId(5L).seq(2).text("Bye.").startMs(500).endMs(900).build()));
		when(sentenceTranslator.translate(List.of("Hi.", "Bye."), "vi")).thenReturn(List.of("Chào.", "Tạm biệt."));

		DictationClipDetailDto detail = service.getClipDetail(5L, "vi");

		assertThat(detail.getSentences().get(0).getTranslation()).isEqualTo("Chào.");
		assertThat(detail.getSentences().get(1).getTranslation()).isEqualTo("Tạm biệt.");
		verify(dictationMapper).updateSentenceTranslation(5L, 1, "Chào.");
		verify(dictationMapper).updateSentenceTranslation(5L, 2, "Tạm biệt.");
	}

	@Test
	void getClipDetailSkipsTranslationWhenLanguageIsEnglish() {
		when(dictationMapper.findClipById(5L)).thenReturn(DictationClip.builder().id(5L).code("c-1").build());
		when(dictationMapper.findSentencesByClipId(5L)).thenReturn(List.of(
				DictationClipSentence.builder().clipId(5L).seq(1).text("Hi.").startMs(0).endMs(500).build()));

		service.getClipDetail(5L, "en");

		verifyNoInteractions(sentenceTranslator);
	}

	@Test
	void getClipDetailSkipsTranslationWhenAlreadyPresent() {
		when(dictationMapper.findClipById(5L)).thenReturn(DictationClip.builder().id(5L).code("c-1").build());
		when(dictationMapper.findSentencesByClipId(5L)).thenReturn(List.of(
				DictationClipSentence.builder().clipId(5L).seq(1).text("Hi.").startMs(0).endMs(500)
						.translation("Chào.").build()));

		DictationClipDetailDto detail = service.getClipDetail(5L, "vi");

		assertThat(detail.getSentences().get(0).getTranslation()).isEqualTo("Chào.");
		verifyNoInteractions(sentenceTranslator);
	}

	@Test
	void getAiPracticeDetailSplitsMultiSpeakerDialogueByLine() {
		when(dictationMapper.findPracticeItemById(10L)).thenReturn(
				DictationPracticeItem.builder().id(10L).sentenceText("A: Hi there.\nB: Hello, how are you?")
						.storageKey("generated/user-1/10.wav").build());

		DictationPracticeItemDetailDto detail = service.getAiPracticeDetail(10L);

		assertThat(detail.getAudioUrl()).isEqualTo("/api/v1/dictation/ai-practice/items/10/audio");
		assertThat(detail.getSentences()).hasSize(2);
		assertThat(detail.getSentences().get(0).getText()).isEqualTo("A: Hi there.");
		assertThat(detail.getSentences().get(1).getText()).isEqualTo("B: Hello, how are you?");
		assertThat(detail.getSentences().get(0).getStartMs()).isNull();
		assertThat(detail.getSentences().get(0).getEndMs()).isNull();
	}

	@Test
	void getAiPracticeDetailSplitsSingleSpeakerMonologueByPunctuation() {
		when(dictationMapper.findPracticeItemById(11L)).thenReturn(
				DictationPracticeItem.builder().id(11L).sentenceText("Hi there. How are you doing today?").build());

		DictationPracticeItemDetailDto detail = service.getAiPracticeDetail(11L);

		assertThat(detail.getAudioUrl()).isNull();
		assertThat(detail.getSentences()).extracting("text")
				.containsExactly("Hi there.", "How are you doing today?");
	}

	@Test
	void getAiPracticeDetailThrowsNotFoundForUnknownItem() {
		when(dictationMapper.findPracticeItemById(99L)).thenReturn(null);

		assertThatThrownBy(() -> service.getAiPracticeDetail(99L)).isInstanceOf(BusinessException.class);
	}

	@Test
	void getAiPracticeDetailZipsStoredTranslationLinesWithSentences() {
		when(dictationMapper.findPracticeItemById(12L)).thenReturn(
				DictationPracticeItem.builder().id(12L)
						.sentenceText("A: Hi there.\nB: Hello, how are you?")
						.translationText("A: Chào bạn.\nB: Xin chào, bạn khỏe không?")
						.level("B1").examType("TOEIC").topic("Greetings")
						.build());

		DictationPracticeItemDetailDto detail = service.getAiPracticeDetail(12L);

		assertThat(detail.getLevel()).isEqualTo("B1");
		assertThat(detail.getExamType()).isEqualTo("TOEIC");
		assertThat(detail.getTopic()).isEqualTo("Greetings");
		assertThat(detail.getSentences().get(0).getTranslation()).isEqualTo("A: Chào bạn.");
		assertThat(detail.getSentences().get(1).getTranslation()).isEqualTo("B: Xin chào, bạn khỏe không?");
	}

	@Test
	void getAiPracticeDetailLeavesTranslationNullWhenNoneStored() {
		when(dictationMapper.findPracticeItemById(13L)).thenReturn(
				DictationPracticeItem.builder().id(13L).sentenceText("Hi there.").build());

		DictationPracticeItemDetailDto detail = service.getAiPracticeDetail(13L);

		assertThat(detail.getSentences().get(0).getTranslation()).isNull();
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
	void generateAiPracticeGeneratesDialogueFromPendingItemsAndReplacesThem() {
		when(dictationMapper.findPracticeItemsWithoutAudio("user-1")).thenReturn(List.of(
				DictationPracticeItem.builder().id(11L).userId("user-1").sentenceText("Listen and write.").build()));
		when(dialogueGenerator.generateDialogue(eq(List.of("Listen and write.")), isNull(), isNull(), isNull()))
				.thenReturn(new DialogueGenerationResult("Study habits",
						List.of(new DictationDialogueLine("Narrator", "Listen and write it down.", null))));
		simulateGeneratedPracticeItemId(21L);
		when(ttsClient.synthesize(any(TtsRequest.class))).thenReturn(
				TtsAudio.builder().audioBytes(new byte[] { 1, 2, 3 }).mimeType("audio/wav").sampleRate(44100).build());
		when(dictationMapper.findPracticeItemsByUserId("user-1")).thenReturn(List.of(
				DictationPracticeItem.builder().id(21L).userId("user-1").sentenceText("Listen and write it down.")
						.topic("Study habits").storageKey("generated/user-1/21.wav").build()));

		List<DictationPracticeItemDto> items = service.generateAiPractice("user-1", new GenerateAiPracticeRequest());

		ArgumentCaptor<DictationPracticeItem> itemCaptor = ArgumentCaptor.forClass(DictationPracticeItem.class);
		verify(dictationMapper).insertPracticeItem(itemCaptor.capture());
		assertThat(itemCaptor.getValue().getTopic()).isEqualTo("Study habits");
		verify(storageClient).write(eq("generated/user-1/21.wav"), any(), eq(3L));
		verify(dictationMapper).updatePracticeItemStorageKey(21L, "generated/user-1/21.wav");
		verify(dictationMapper).deletePracticeItemsWithoutAudio("user-1");
		assertThat(items).hasSize(1);
		assertThat(items.get(0).getAudioUrl()).isEqualTo("/api/v1/dictation/ai-practice/items/21/audio");
		assertThat(items.get(0).getTopic()).isEqualTo("Study habits");
	}

	@Test
	void generateAiPracticeUsesTopMissedWordsWhenNonePending() {
		when(dictationMapper.findPracticeItemsWithoutAudio("user-1")).thenReturn(List.of());
		when(dictationMapper.findTopMissedWords("user-1", 8)).thenReturn(List.of(
				MissWordCount.builder().word("reluctant").missCount(3).build()));
		when(dialogueGenerator.generateDialogue(eq(List.of("reluctant")), isNull(), isNull(), isNull()))
				.thenReturn(new DialogueGenerationResult(null,
						List.of(new DictationDialogueLine("Narrator", "She was reluctant to leave.", null))));
		simulateGeneratedPracticeItemId(21L);
		when(ttsClient.synthesize(any(TtsRequest.class))).thenReturn(
				TtsAudio.builder().audioBytes(new byte[] { 9 }).mimeType("audio/wav").build());
		when(dictationMapper.findPracticeItemsByUserId("user-1")).thenReturn(List.of());

		service.generateAiPractice("user-1", new GenerateAiPracticeRequest());

		verify(dictationMapper).insertPracticeItem(any(DictationPracticeItem.class));
		verify(storageClient).write(eq("generated/user-1/21.wav"), any(), eq(1L));
		verify(dictationMapper, org.mockito.Mockito.never()).deletePracticeItemsWithoutAudio(anyString());
	}

	@Test
	void generateAiPracticeAssignsDistinctVoicesPerSpeakerAndMergesAudioWithSpeakerNameSpoken() throws java.io.IOException {
		when(dictationMapper.findPracticeItemsWithoutAudio("user-1")).thenReturn(List.of());
		when(dictationMapper.findTopMissedWords("user-1", 8)).thenReturn(List.of());
		when(dialogueGenerator.generateDialogue(eq(List.of()), isNull(), isNull(), isNull())).thenReturn(
				new DialogueGenerationResult(null, List.of(
						new DictationDialogueLine("Alex", "Did you see that?", null),
						new DictationDialogueLine("Sam", "Yes, I did.", null))));
		simulateGeneratedPracticeItemId(40L);
		when(ttsClient.synthesize(any(TtsRequest.class)))
				.thenReturn(TtsAudio.builder().audioBytes(buildWav(new byte[] { 1, 2 })).mimeType("audio/wav").build())
				.thenReturn(TtsAudio.builder().audioBytes(buildWav(new byte[] { 3, 4, 5 })).mimeType("audio/wav").build());
		when(dictationMapper.findPracticeItemsByUserId("user-1")).thenReturn(List.of());

		service.generateAiPractice("user-1", new GenerateAiPracticeRequest());

		ArgumentCaptor<TtsRequest> requestCaptor = ArgumentCaptor.forClass(TtsRequest.class);
		verify(ttsClient, org.mockito.Mockito.times(2)).synthesize(requestCaptor.capture());
		List<String> voicesUsed = requestCaptor.getAllValues().stream().map(TtsRequest::getVoice).distinct().toList();
		assertThat(voicesUsed).hasSize(2);
		assertThat(voicesUsed).allMatch(voice -> VOICE_POOL.contains(voice));
		// Bug fix: the TTS audio must speak the exact same "Speaker: text" the learner is graded
		// against, not just the bare line - otherwise the audio never says the name the answer key expects.
		List<String> spokenTexts = requestCaptor.getAllValues().stream().map(TtsRequest::getText).toList();
		assertThat(spokenTexts).containsExactly("Alex: Did you see that?", "Sam: Yes, I did.");

		ArgumentCaptor<InputStream> audioCaptor = ArgumentCaptor.forClass(InputStream.class);
		verify(storageClient).write(eq("generated/user-1/40.wav"), audioCaptor.capture(), eq(49L));
		assertThat(audioCaptor.getValue().readAllBytes()).hasSize(49);

		ArgumentCaptor<DictationPracticeItem> itemCaptor = ArgumentCaptor.forClass(DictationPracticeItem.class);
		verify(dictationMapper).insertPracticeItem(itemCaptor.capture());
		assertThat(itemCaptor.getValue().getSentenceText()).isEqualTo("Alex: Did you see that?\nSam: Yes, I did.");
	}

	@Test
	void generateAiPracticeResolvesRandomLevelFromTheFixedPoolAndPersistsIt() {
		when(dictationMapper.findPracticeItemsWithoutAudio("user-1")).thenReturn(List.of());
		when(dictationMapper.findTopMissedWords("user-1", 8)).thenReturn(List.of());
		when(dialogueGenerator.generateDialogue(eq(List.of()), any(), isNull(), isNull())).thenReturn(
				new DialogueGenerationResult("Weekend plans", List.of(new DictationDialogueLine("Narrator", "Let's go.", null))));
		simulateGeneratedPracticeItemId(50L);
		when(ttsClient.synthesize(any(TtsRequest.class))).thenReturn(
				TtsAudio.builder().audioBytes(new byte[] { 1 }).mimeType("audio/wav").build());
		when(dictationMapper.findPracticeItemsByUserId("user-1")).thenReturn(List.of());

		GenerateAiPracticeRequest request = new GenerateAiPracticeRequest();
		request.setLevel("RANDOM");
		service.generateAiPractice("user-1", request);

		ArgumentCaptor<String> levelCaptor = ArgumentCaptor.forClass(String.class);
		verify(dialogueGenerator).generateDialogue(eq(List.of()), levelCaptor.capture(), isNull(), isNull());
		assertThat(levelCaptor.getValue()).isIn("A1", "A2", "B1", "B2", "C1");

		ArgumentCaptor<DictationPracticeItem> itemCaptor = ArgumentCaptor.forClass(DictationPracticeItem.class);
		verify(dictationMapper).insertPracticeItem(itemCaptor.capture());
		assertThat(itemCaptor.getValue().getLevel()).isEqualTo(levelCaptor.getValue());
	}

	@Test
	void generateAiPracticeResolvesRandomExamTypeFromFacetsFallingBackWhenEmpty() {
		when(dictationMapper.findPracticeItemsWithoutAudio("user-1")).thenReturn(List.of());
		when(dictationMapper.findTopMissedWords("user-1", 8)).thenReturn(List.of());
		when(dictationMapper.findDistinctExamTypes()).thenReturn(List.of());
		when(dialogueGenerator.generateDialogue(eq(List.of()), isNull(), any(), isNull())).thenReturn(
				new DialogueGenerationResult(null, List.of(new DictationDialogueLine("Narrator", "Let's go.", null))));
		simulateGeneratedPracticeItemId(51L);
		when(ttsClient.synthesize(any(TtsRequest.class))).thenReturn(
				TtsAudio.builder().audioBytes(new byte[] { 1 }).mimeType("audio/wav").build());
		when(dictationMapper.findPracticeItemsByUserId("user-1")).thenReturn(List.of());

		GenerateAiPracticeRequest request = new GenerateAiPracticeRequest();
		request.setExamType("RANDOM");
		service.generateAiPractice("user-1", request);

		ArgumentCaptor<String> examTypeCaptor = ArgumentCaptor.forClass(String.class);
		verify(dialogueGenerator).generateDialogue(eq(List.of()), isNull(), examTypeCaptor.capture(), isNull());
		assertThat(examTypeCaptor.getValue()).isIn("TOEIC", "IELTS", "TOEFL", "General");
	}

	private static final List<String> VOICE_POOL = List.of("F1", "F2", "F3", "F4", "F5", "M1", "M2", "M3", "M4", "M5");

	// Builds a minimal canonical-header WAV (44-byte header + given PCM payload) so WavAudioMerger
	// can locate its "fmt "/"data" chunks the same way it would for real Supertonic output.
	private static byte[] buildWav(byte[] pcmData) {
		java.nio.ByteBuffer buffer = java.nio.ByteBuffer.allocate(44 + pcmData.length).order(java.nio.ByteOrder.LITTLE_ENDIAN);
		buffer.put("RIFF".getBytes()).putInt(36 + pcmData.length).put("WAVE".getBytes());
		buffer.put("fmt ".getBytes()).putInt(16).put(new byte[16]);
		buffer.put("data".getBytes()).putInt(pcmData.length).put(pcmData);
		return buffer.array();
	}

	@Test
	void generateAiPracticeFromAttemptGeneratesADialogueFromThatAttemptsMissesAndSynthesizesAudio() {
		when(dictationMapper.findAttemptDetailByIdAndUserId(500L, "user-1")).thenReturn(
				DictationAttemptDetailRow.builder().attemptId(500L).clipId(42L).build());
		when(dictationMapper.findMissesByAttemptId(500L)).thenReturn(List.of(
				DictationMiss.builder().attemptId(500L).userId("user-1").expectedWord("Reluctant").build(),
				DictationMiss.builder().attemptId(500L).userId("user-1").expectedWord("reluctant").build()));
		when(dialogueGenerator.generateDialogue(eq(List.of("reluctant")), isNull(), isNull(), isNull()))
				.thenReturn(new DialogueGenerationResult("Making excuses",
						List.of(new DictationDialogueLine("Narrator", "She was reluctant to leave.", null))));
		simulateGeneratedPracticeItemId(31L);
		when(ttsClient.synthesize(any(TtsRequest.class))).thenReturn(
				TtsAudio.builder().audioBytes(new byte[] { 4, 5 }).mimeType("audio/wav").build());
		when(dictationMapper.findPracticeItemsByUserId("user-1")).thenReturn(List.of());

		service.generateAiPracticeFromAttempt("user-1", 500L, null);

		// Duplicate/differently-cased misses collapse to one distinct word before generation.
		verify(dialogueGenerator).generateDialogue(List.of("reluctant"), null, null, null);
		verify(dictationAnalyzer, org.mockito.Mockito.never()).generatePracticeSentences(anyList());
		verify(dictationMapper).insertPracticeItem(any(DictationPracticeItem.class));
		verify(storageClient).write(eq("generated/user-1/31.wav"), any(), eq(2L));
	}

	@Test
	void generateAiPracticeFromAttemptThrowsNotFoundWhenMissingOrNotOwned() {
		when(dictationMapper.findAttemptDetailByIdAndUserId(999L, "user-1")).thenReturn(null);

		assertThatThrownBy(() -> service.generateAiPracticeFromAttempt("user-1", 999L, null))
				.isInstanceOf(BusinessException.class);
	}

	@Test
	void getHistoryMarksLibraryAndAiPracticeEntriesByPresenceOfClipId() {
		when(dictationMapper.findHistoryByUserId("user-1")).thenReturn(List.of(
				DictationHistoryRow.builder().attemptId(1L).clipId(42L).attemptCount(2).build(),
				DictationHistoryRow.builder().attemptId(2L).clipId(null).build()));

		List<DictationHistoryEntryDto> history = service.getHistory("user-1");

		assertThat(history.get(0).getPracticeType()).isEqualTo(DictationPracticeType.LIBRARY);
		assertThat(history.get(1).getPracticeType()).isEqualTo(DictationPracticeType.AI_PRACTICE);
	}

	@Test
	void submitAttemptPersistsAiAnalysisAsJson() throws Exception {
		when(dictationMapper.findClipById(42L)).thenReturn(DictationClip.builder()
				.id(42L).code("c-1").scriptText("She was reluctant to admit it.").build());
		simulateGeneratedAttemptId(600L);
		when(dictationAnalyzer.analyzeAttempt(anyString(), anyString(), anyList())).thenReturn(DictationAnalysis.builder()
				.errorTable(List.of())
				.rootCauses(List.of())
				.actionAdvice(List.of("Nghe lại từ 'reluctant'.", "Chú ý âm cuối 'admit'."))
				.practiceSentences(List.of())
				.build());
		when(dictationMapper.countMissesForWord(eq("user-1"), anyString())).thenReturn(1);

		DictationAttemptRequest request = new DictationAttemptRequest();
		request.setUserId("user-1");
		request.setClipId(42L);
		request.setUserTranscript("She was to admit it.");

		service.submitAttempt(request);

		ArgumentCaptor<String> jsonCaptor = ArgumentCaptor.forClass(String.class);
		verify(dictationMapper).updateAttemptAiSuggestions(eq(600L), jsonCaptor.capture());
		DictationAnalysis persisted = objectMapper.readValue(jsonCaptor.getValue(), DictationAnalysis.class);
		assertThat(persisted.getActionAdvice()).containsExactly("Nghe lại từ 'reluctant'.", "Chú ý âm cuối 'admit'.");
	}

	@Test
	void getAttemptDetailReturnsFullDetailForOwnedAttempt() {
		when(dictationMapper.findAttemptDetailByIdAndUserId(500L, "user-1")).thenReturn(
				DictationAttemptDetailRow.builder()
						.attemptId(500L).clipId(42L).title("Photocopy").skill("Listening").level("B1").examType("TOEIC")
						.referenceText("She was reluctant to admit it.")
						.userTranscript("She was to admit it.")
						.accuracy(0.8).wer(0.2)
						.aiSuggestions("{\"errorTable\":[],\"rootCauses\":[],\"actionAdvice\":[\"Nghe lại từ 'reluctant'.\"],\"practiceSentences\":[]}")
						.build());
		when(dictationMapper.findMissesByAttemptId(500L)).thenReturn(List.of(
				DictationMiss.builder().attemptId(500L).userId("user-1").clipId(42L)
						.expectedWord("reluctant").actualWord(null).tag("MISSING").build()));

		DictationAttemptDetailDto detail = service.getAttemptDetail("user-1", 500L);

		assertThat(detail.getTitle()).isEqualTo("Photocopy");
		assertThat(detail.getReferenceText()).isEqualTo("She was reluctant to admit it.");
		assertThat(detail.getMistakes()).hasSize(1);
		assertThat(detail.getMistakes().get(0).getExpectedWord()).isEqualTo("reluctant");
		assertThat(detail.getMistakes().get(0).getTag()).isEqualTo(WordDiffTag.MISSING);
		assertThat(detail.getActionAdvice()).containsExactly("Nghe lại từ 'reluctant'.");
	}

	@Test
	void getAttemptDetailReadsLegacyPlainStringArrayFormatAsActionAdvice() {
		// Attempts persisted before the root-cause analysis shipped stored a plain JSON string array.
		when(dictationMapper.findAttemptDetailByIdAndUserId(505L, "user-1")).thenReturn(
				DictationAttemptDetailRow.builder()
						.attemptId(505L).referenceText("Hi.").userTranscript("Hi.")
						.accuracy(1.0).wer(0.0)
						.aiSuggestions("[\"Nghe lại từ 'reluctant'.\"]")
						.build());
		when(dictationMapper.findMissesByAttemptId(505L)).thenReturn(List.of());

		DictationAttemptDetailDto detail = service.getAttemptDetail("user-1", 505L);

		assertThat(detail.getActionAdvice()).containsExactly("Nghe lại từ 'reluctant'.");
		assertThat(detail.getErrorTable()).isEmpty();
		assertThat(detail.getRootCauses()).isEmpty();
	}

	@Test
	void getAttemptDetailThrowsNotFoundWhenMissingOrNotOwned() {
		when(dictationMapper.findAttemptDetailByIdAndUserId(999L, "user-1")).thenReturn(null);

		assertThatThrownBy(() -> service.getAttemptDetail("user-1", 999L)).isInstanceOf(BusinessException.class);
	}

	@Test
	void getAttemptDetailDefaultsToEmptySuggestionsWhenColumnIsNull() {
		when(dictationMapper.findAttemptDetailByIdAndUserId(501L, "user-1")).thenReturn(
				DictationAttemptDetailRow.builder()
						.attemptId(501L).referenceText("Hi.").userTranscript("Hi.")
						.accuracy(1.0).wer(0.0).aiSuggestions(null)
						.build());
		when(dictationMapper.findMissesByAttemptId(501L)).thenReturn(List.of());

		DictationAttemptDetailDto detail = service.getAttemptDetail("user-1", 501L);

		assertThat(detail.getActionAdvice()).isEmpty();
		assertThat(detail.getMistakes()).isEmpty();
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
