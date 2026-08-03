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
import com.remelearning.english.listening.generator.ListeningSessionRequest;
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
	void generatePersistsAWholeSessionOfPassagesWithoutSynthesizingAnyAudioUpFront() {
		when(mapper.findItemsByUserId("user-1")).thenReturn(List.of());
		when(generator.generate(any(ListeningSessionRequest.class))).thenReturn(List.of(
				passage("Travel", "We will be departing shortly."),
				passage("Hotel check-in", "I'm afraid your room isn't ready yet."),
				passage("Weather", "Expect heavy rain across the region tonight.")));
		simulateGeneratedItemId(5L);

		List<ListeningPracticeItemDto> dtos = service.generate("user-1", new GenerateListeningPracticeRequest());

		assertThat(dtos).hasSize(3);
		assertThat(dtos.get(0).getPracticeItemId()).isEqualTo(5L);
		// Advertised even though nothing has been synthesized yet - requesting it is what triggers
		// synthesis (see loadAudioSynthesizesOnFirstPlayAndReusesTheStoredKeyAfterwards).
		assertThat(dtos.get(0).getAudioUrl()).isEqualTo("/api/v1/learners/user-1/learn/listening/items/5/audio");
		assertThat(dtos.get(0).getQuestions()).hasSize(1);
		verify(mapper, org.mockito.Mockito.times(3)).insertItem(any(ListeningPracticeItem.class));
		verify(audioSynthesizer, org.mockito.Mockito.never()).synthesize(any(), anyString());
		verify(storageClient, org.mockito.Mockito.never()).write(anyString(), any(), org.mockito.ArgumentMatchers.anyLong());
	}

	@Test
	void generateAsksForFiveToTenPassagesAndTellsTheGeneratorWhichTopicsToAvoid() {
		when(mapper.findItemsByUserId("user-1")).thenReturn(List.of(
				ListeningPracticeItem.builder().id(1L).userId("user-1").topic("Travel")
						.questionsJson(toJson(List.of(ListeningQuestionItem.builder()
								.type(ListeningQuestionType.KEYWORD).skill("keyword").prompt("p").answer("departing").build())))
						.build()));
		when(generator.generate(any(ListeningSessionRequest.class)))
				.thenReturn(List.of(passage("Hotel check-in", "I'm afraid your room isn't ready yet.")));
		simulateGeneratedItemId(9L);

		service.generate("user-1", new GenerateListeningPracticeRequest());

		ArgumentCaptor<ListeningSessionRequest> captor = ArgumentCaptor.forClass(ListeningSessionRequest.class);
		verify(generator).generate(captor.capture());
		assertThat(captor.getValue().passageCount()).isBetween(5, 10);
		assertThat(captor.getValue().avoidTopics()).containsExactly("Travel");
		assertThat(captor.getValue().targetKeywords()).containsExactly("departing");
	}

	@Test
	void loadAudioSynthesizesOnFirstPlayAndReusesTheStoredKeyAfterwards() {
		ListeningPracticeItem pending = ListeningPracticeItem.builder()
				.id(5L).userId("user-1").transcript("We will be departing shortly.")
				.linesJson(toJson(List.of(new DialogueLine("A", "We will be departing shortly.", null))))
				.questionsJson("[]")
				.build();
		when(mapper.findItemById(5L)).thenReturn(pending);
		when(audioSynthesizer.synthesize(any(), eq("en"))).thenReturn(
				new SynthesizedDialogue("wav-bytes".getBytes(), "We will be departing shortly.", null));

		service.loadAudio(5L);

		verify(storageClient).write(eq("listening/user-1/5.opus"), any(), eq(9L));
		verify(mapper).updateItemStorageKey(5L, "listening/user-1/5.opus");

		// Second play: the key is already recorded, so nothing is re-synthesized.
		when(mapper.findItemById(5L)).thenReturn(ListeningPracticeItem.builder()
				.id(5L).userId("user-1").storageKey("listening/user-1/5.opus").questionsJson("[]").build());

		service.loadAudio(5L);

		verify(audioSynthesizer, org.mockito.Mockito.times(1)).synthesize(any(), eq("en"));
	}

	@Test
	void loadAudioThrowsNotFoundForALegacyItemThatHasNeitherAudioNorLinesToRebuildItFrom() {
		when(mapper.findItemById(5L)).thenReturn(ListeningPracticeItem.builder()
				.id(5L).userId("user-1").questionsJson("[]").build());

		assertThatThrownBy(() -> service.loadAudio(5L)).isInstanceOf(BusinessException.class);
	}

	@Test
	void listItemsReturnsOnlyTheSessionPassagesTheLearnerHasNotAttemptedYet() {
		when(mapper.findPendingItemsByUserId("user-1")).thenReturn(List.of(
				ListeningPracticeItem.builder().id(7L).userId("user-1").topic("Hotel check-in")
						.linesJson("[]").questionsJson("[]").build()));

		List<ListeningPracticeItemDto> dtos = service.listItems("user-1");

		assertThat(dtos).extracting(ListeningPracticeItemDto::getPracticeItemId).containsExactly(7L);
		verify(mapper, org.mockito.Mockito.never()).findItemsByUserId("user-1");
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
		when(generator.generate(any(ListeningSessionRequest.class)))
				.thenReturn(List.of(passage("Travel retry", "Flight 204 is now boarding.")));
		simulateGeneratedItemId(6L);

		List<ListeningPracticeItemDto> result = service.generatePracticeFromAttempt("user-1", 30L);

		ArgumentCaptor<ListeningSessionRequest> captor = ArgumentCaptor.forClass(ListeningSessionRequest.class);
		verify(generator).generate(captor.capture());
		assertThat(captor.getValue().targetKeywords()).containsExactly("A flight departure");
		assertThat(captor.getValue().level()).isEqualTo("B1");
		assertThat(captor.getValue().examType()).isEqualTo("TOEIC");
		verify(mapper).insertItem(any(ListeningPracticeItem.class));
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
		when(generator.generate(any(ListeningSessionRequest.class)))
				.thenReturn(List.of(passage("Travel retry", "Flight 204 is now boarding.")));
		simulateGeneratedItemId(7L);

		List<ListeningPracticeItemDto> result = service.generatePracticeFromAttempt("user-1", 31L);

		ArgumentCaptor<ListeningSessionRequest> captor = ArgumentCaptor.forClass(ListeningSessionRequest.class);
		verify(generator).generate(captor.capture());
		assertThat(captor.getValue().targetKeywords()).containsExactly("Travel");
		assertThat(result).isNotNull();
	}

	// One generated monologue passage with a single MCQ question - the shape the generator now returns
	// N of per session.
	private GeneratedListeningPractice passage(String topic, String text) {
		return new GeneratedListeningPractice(topic, List.of(new DialogueLine("A", text, null)),
				List.of(ListeningQuestionItem.builder().type(ListeningQuestionType.MCQ).skill("main-idea")
						.prompt("What is this about?").options(List.of("A flight", "A train"))
						.answer("A flight").explanation("x").build()));
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
