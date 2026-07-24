package com.remelearning.english.listening.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.remelearning.common.exception.BusinessException;
import com.remelearning.common.storage.StorageClient;
import com.remelearning.english.learn.common.DialogueAudioSynthesizer;
import com.remelearning.english.learn.common.DialogueLine;
import com.remelearning.english.learn.common.SynthesizedDialogue;
import com.remelearning.english.listening.domain.ListeningAttemptDetailRow;
import com.remelearning.english.listening.domain.ListeningPracticeItem;
import com.remelearning.english.listening.domain.ListeningQuestionItem;
import com.remelearning.english.listening.domain.ListeningQuestionType;
import com.remelearning.english.listening.dto.GenerateListeningPracticeRequest;
import com.remelearning.english.listening.dto.ListeningAttemptDetailDto;
import com.remelearning.english.listening.dto.ListeningAttemptQuestionResultDto;
import com.remelearning.english.listening.dto.ListeningAttemptResultDto;
import com.remelearning.english.listening.dto.ListeningPracticeItemDto;
import com.remelearning.english.listening.dto.SubmitListeningAttemptRequest;
import com.remelearning.english.listening.generator.GeneratedListeningPractice;
import com.remelearning.english.listening.generator.ListeningPracticeGenerator;
import com.remelearning.english.listening.mapper.ListeningMapper;
import com.remelearning.english.listening.scoring.OpenAnswerGrade;
import com.remelearning.english.listening.scoring.OpenAnswerGrader;
import com.remelearning.english.practice.dto.PracticeRedoRequest;
import com.remelearning.english.practice.service.PracticeService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

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

class ListeningLearnServiceImplTest {

	private final ListeningMapper mapper = mock(ListeningMapper.class);
	private final ListeningPracticeGenerator generator = mock(ListeningPracticeGenerator.class);
	private final DialogueAudioSynthesizer audioSynthesizer = mock(DialogueAudioSynthesizer.class);
	private final OpenAnswerGrader openAnswerGrader = mock(OpenAnswerGrader.class);
	private final StorageClient storageClient = mock(StorageClient.class);
	private final PracticeService practiceService = mock(PracticeService.class);
	private final ObjectMapper objectMapper = new ObjectMapper();
	private final ListeningLearnServiceImpl service = new ListeningLearnServiceImpl(
			mapper, generator, audioSynthesizer, openAnswerGrader, storageClient, practiceService, objectMapper, "en");

	@Test
	void generateSynthesizesAudioAndHidesTranscriptFromTheItemDto() {
		when(mapper.findItemsByUserId("user-1")).thenReturn(List.of());
		when(generator.generate(eq(List.of()), any(), any(), any())).thenReturn(new GeneratedListeningPractice(
				"Travel", List.of(new DialogueLine("A", "We will be departing shortly.", null)),
				List.of(ListeningQuestionItem.builder().type(ListeningQuestionType.MCQ).skill("main-idea")
						.prompt("What is this about?").options(List.of("A flight", "A train")).answer("A flight").explanation("x").build())));
		when(audioSynthesizer.synthesize(any(), eq("en"))).thenReturn(
				new SynthesizedDialogue("wav-bytes".getBytes(), "We will be departing shortly.", null));
		simulateGeneratedItemId(5L);

		ListeningPracticeItemDto dto = service.generate("user-1", new GenerateListeningPracticeRequest());

		assertThat(dto.getPracticeItemId()).isEqualTo(5L);
		assertThat(dto.getAudioUrl()).isEqualTo("/api/v1/learners/user-1/learn/listening/items/5/audio");
		assertThat(dto.getQuestions()).hasSize(1);
		verify(storageClient).write(eq("listening/user-1/5.wav"), any(), eq(9L));
		verify(mapper).updateItemStorageKey(5L, "listening/user-1/5.wav");
	}

	@Test
	void submitGradesMcqKeywordAndOpenQuestionsAndFeedsWeakPointPipeline() {
		ListeningPracticeItem item = ListeningPracticeItem.builder()
				.id(20L).userId("user-1").transcript("We will be departing shortly.")
				.questionsJson(toJson(List.of(
						ListeningQuestionItem.builder().type(ListeningQuestionType.MCQ).skill("main-idea")
								.prompt("p1").options(List.of("A flight", "A train")).answer("A flight").explanation("e1").build(),
						ListeningQuestionItem.builder().type(ListeningQuestionType.KEYWORD).skill("keyword")
								.prompt("p2").answer("departing").explanation("e2").build(),
						ListeningQuestionItem.builder().type(ListeningQuestionType.OPEN).skill("open-response")
								.prompt("p3").answer("model answer").explanation("e3").build())))
				.build();
		when(mapper.findItemById(20L)).thenReturn(item);
		when(openAnswerGrader.grade(anyString(), anyString(), anyString(), any()))
				.thenReturn(new OpenAnswerGrade(0.8, "Trả lời khá tốt."));

		SubmitListeningAttemptRequest request = new SubmitListeningAttemptRequest();
		request.setUserId("user-1");
		request.setPracticeItemId(20L);
		request.setAnswers(List.of("A train", "departing", "some open answer"));

		ListeningAttemptResultDto result = service.submit(request);

		assertThat(result.getResults()).hasSize(3);
		assertThat(result.getResults().get(0).isCorrect()).isFalse();
		assertThat(result.getResults().get(1).isCorrect()).isTrue();
		assertThat(result.getResults().get(2).isCorrect()).isTrue();
		assertThat(result.getResults().get(2).getSubScore()).isEqualTo(0.8);
		assertThat(result.getTranscript()).isEqualTo("We will be departing shortly.");

		ArgumentCaptor<PracticeRedoRequest> redoCaptor = ArgumentCaptor.forClass(PracticeRedoRequest.class);
		verify(practiceService).redo(redoCaptor.capture());
		assertThat(redoCaptor.getValue().getAttempts()).extracting(a -> a.getCategory()).containsOnly("listening");
	}

	@Test
	void getItemThrowsNotFoundForUnknownId() {
		when(mapper.findItemById(99L)).thenReturn(null);

		assertThatThrownBy(() -> service.getItem(99L)).isInstanceOf(BusinessException.class);
	}

	@Test
	void getAttemptDetailReadsPersistedResultsWithoutReGrading() {
		ListeningAttemptDetailRow row = ListeningAttemptDetailRow.builder()
				.attemptId(30L).level("B1").examType("TOEIC").topic("Travel")
				.transcript("We will be departing shortly.").translation(null)
				.resultsJson(toJson(List.of(ListeningAttemptQuestionResultDto.builder()
						.index(0).prompt("p1").yourAnswer("A flight").correctAnswer("A flight")
						.correct(true).subScore(1.0).explanation("e1").build())))
				.score(1.0)
				.createdAt(Instant.now())
				.build();
		when(mapper.findAttemptDetailByIdAndUserId(30L, "user-1")).thenReturn(row);

		ListeningAttemptDetailDto dto = service.getAttemptDetail("user-1", 30L);

		assertThat(dto.getResults()).hasSize(1);
		assertThat(dto.getResults().get(0).isCorrect()).isTrue();
		verify(openAnswerGrader, org.mockito.Mockito.never()).grade(any(), any(), any(), any());
	}

	@Test
	void generatePracticeFromAttemptThrowsNotFoundForUnknownOrForeignAttempt() {
		when(mapper.findAttemptDetailByIdAndUserId(99L, "user-1")).thenReturn(null);

		assertThatThrownBy(() -> service.generatePracticeFromAttempt("user-1", 99L))
				.isInstanceOf(BusinessException.class);
	}

	@Test
	void generatePracticeFromAttemptRegeneratesTargetingTheDistinctMissedCorrectAnswersAndPersistsIntoTheSameBank() {
		ListeningAttemptDetailRow attempt = ListeningAttemptDetailRow.builder()
				.attemptId(30L).level("B1").examType("TOEIC").topic("Travel")
				.transcript("We will be departing shortly.").translation(null)
				.resultsJson(toJson(List.of(
						ListeningAttemptQuestionResultDto.builder().index(0).prompt("p1")
								.correctAnswer("A flight departure").correct(false).subScore(0.0).build(),
						ListeningAttemptQuestionResultDto.builder().index(1).prompt("p2")
								.correctAnswer("departing").correct(true).subScore(1.0).build())))
				.score(0.5)
				.createdAt(Instant.now())
				.build();
		when(mapper.findAttemptDetailByIdAndUserId(30L, "user-1")).thenReturn(attempt);
		when(mapper.findItemsByUserId("user-1")).thenReturn(List.of());
		when(generator.generate(eq(List.of("A flight departure")), eq("B1"), eq("TOEIC"), any())).thenReturn(
				new GeneratedListeningPractice("Travel retry",
						List.of(new DialogueLine("A", "Flight 204 is now boarding.", null)),
						List.of(ListeningQuestionItem.builder().type(ListeningQuestionType.MCQ).skill("main-idea")
								.prompt("What is this about?").options(List.of("A flight", "A train")).answer("A flight").explanation("x").build())));
		when(audioSynthesizer.synthesize(any(), eq("en"))).thenReturn(
				new SynthesizedDialogue("wav-bytes".getBytes(), "Flight 204 is now boarding.", null));
		simulateGeneratedItemId(6L);

		List<ListeningPracticeItemDto> result = service.generatePracticeFromAttempt("user-1", 30L);

		verify(mapper).insertItem(any(ListeningPracticeItem.class));
		verify(mapper).updateItemStorageKey(6L, "listening/user-1/6.wav");
		assertThat(result).isNotNull();
	}

	@Test
	void generatePracticeFromAttemptUsesTheAttemptTopicNameForMissedOpenQuestions() {
		ListeningAttemptDetailRow attempt = ListeningAttemptDetailRow.builder()
				.attemptId(31L).level("B1").examType("TOEIC").topic("Travel")
				.transcript("We will be departing shortly.").translation(null)
				.resultsJson(toJson(List.of(
						ListeningAttemptQuestionResultDto.builder().index(0).prompt("p1")
								.correctAnswer("The speaker is worried about missing the connecting flight.")
								.correct(false).subScore(0.1).type(ListeningQuestionType.OPEN).build())))
				.score(0.1)
				.createdAt(Instant.now())
				.build();
		when(mapper.findAttemptDetailByIdAndUserId(31L, "user-1")).thenReturn(attempt);
		when(mapper.findItemsByUserId("user-1")).thenReturn(List.of());
		when(generator.generate(eq(List.of("Travel")), eq("B1"), eq("TOEIC"), any())).thenReturn(
				new GeneratedListeningPractice("Travel retry",
						List.of(new DialogueLine("A", "Flight 204 is now boarding.", null)),
						List.of(ListeningQuestionItem.builder().type(ListeningQuestionType.MCQ).skill("main-idea")
								.prompt("What is this about?").options(List.of("A flight", "A train")).answer("A flight").explanation("x").build())));
		when(audioSynthesizer.synthesize(any(), eq("en"))).thenReturn(
				new SynthesizedDialogue("wav-bytes".getBytes(), "Flight 204 is now boarding.", null));
		simulateGeneratedItemId(7L);

		List<ListeningPracticeItemDto> result = service.generatePracticeFromAttempt("user-1", 31L);

		verify(generator).generate(eq(List.of("Travel")), eq("B1"), eq("TOEIC"), any());
		assertThat(result).isNotNull();
	}

	private void simulateGeneratedItemId(Long id) {
		org.mockito.Mockito.doAnswer(invocation -> {
			ListeningPracticeItem item = invocation.getArgument(0);
			item.setId(id);
			return null;
		}).when(mapper).insertItem(any(ListeningPracticeItem.class));
	}

	private String toJson(Object value) {
		try {
			return objectMapper.writeValueAsString(value);
		} catch (Exception ex) {
			throw new RuntimeException(ex);
		}
	}
}
