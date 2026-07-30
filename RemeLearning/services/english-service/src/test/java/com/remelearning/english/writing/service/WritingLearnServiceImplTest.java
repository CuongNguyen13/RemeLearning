package com.remelearning.english.writing.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.remelearning.common.exception.BusinessException;
import com.remelearning.english.grammar.domain.GrammarWeakPoint;
import com.remelearning.english.grammar.service.GrammarWeakPointService;
import com.remelearning.english.practice.dto.PracticeAttemptRequest;
import com.remelearning.english.practice.dto.PracticeRedoRequest;
import com.remelearning.english.practice.service.PracticeService;
import com.remelearning.english.vocabulary.domain.VocabularyWeakPoint;
import com.remelearning.english.vocabulary.service.VocabularyWeakPointService;
import com.remelearning.english.writing.domain.WritingAttempt;
import com.remelearning.english.writing.domain.WritingAttemptDetailRow;
import com.remelearning.english.writing.domain.WritingCriteriaScores;
import com.remelearning.english.writing.domain.WritingErrorItem;
import com.remelearning.english.writing.domain.WritingPracticeItem;
import com.remelearning.english.writing.domain.WritingSuggestion;
import com.remelearning.english.writing.domain.WritingTaskType;
import com.remelearning.english.writing.dto.GenerateWritingPracticeRequest;
import com.remelearning.english.writing.dto.SubmitWritingAttemptRequest;
import com.remelearning.english.writing.dto.SuggestNextSentenceRequest;
import com.remelearning.english.writing.dto.WritingAttemptResultDto;
import com.remelearning.english.writing.dto.WritingPracticeItemDto;
import com.remelearning.english.writing.generator.GeneratedWritingPractice;
import com.remelearning.english.writing.generator.WritingPracticeGenerator;
import com.remelearning.english.writing.grading.WritingErrorPipeline;
import com.remelearning.english.writing.grading.WritingGrade;
import com.remelearning.english.writing.grading.WritingGrader;
import com.remelearning.english.writing.mapper.WritingMapper;
import com.remelearning.english.writing.suggestion.NextSentenceSuggester;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class WritingLearnServiceImplTest {

	private final WritingMapper mapper = mock(WritingMapper.class);
	private final WritingPracticeGenerator generator = mock(WritingPracticeGenerator.class);
	private final WritingGrader grader = mock(WritingGrader.class);
	private final NextSentenceSuggester suggester = mock(NextSentenceSuggester.class);
	private final GrammarWeakPointService grammarWeakPointService = mock(GrammarWeakPointService.class);
	private final VocabularyWeakPointService vocabularyWeakPointService = mock(VocabularyWeakPointService.class);
	private final PracticeService practiceService = mock(PracticeService.class);
	private final ObjectMapper objectMapper = new ObjectMapper();
	// The real pipeline, not a mock: the weak-point routing (item-id prefixes, dedupe, skipping
	// unroutable categories) is the subtle part of this flow and is worth exercising for real here.
	private final WritingErrorPipeline errorPipeline = new WritingErrorPipeline(practiceService);
	private final WritingLearnServiceImpl service = new WritingLearnServiceImpl(
			mapper, generator, grader, suggester, errorPipeline, grammarWeakPointService,
			vocabularyWeakPointService, objectMapper);

	@Test
	void generateFallsBackToBothGrammarAndVocabularyWeakPointsWhenNoFocusItemsGiven() {
		when(grammarWeakPointService.getTopWeakPoints("user-1", 8))
				.thenReturn(List.of(weakGrammar("past perfect"), weakGrammar("article usage")));
		when(vocabularyWeakPointService.getTopWeakPoints("user-1", 8))
				.thenReturn(List.of(weakVocabulary("collocation: make/do")));
		when(generator.generate(any(), any(), any(), any()))
				.thenReturn(new GeneratedWritingPractice("Daily routine", "Viết một đoạn văn...", "Model answer."));
		simulateGeneratedItemId(7L);

		GenerateWritingPracticeRequest request = new GenerateWritingPracticeRequest();
		request.setTaskType(WritingTaskType.COMPOSE);
		request.setLevel("B1");
		WritingPracticeItemDto dto = service.generate("user-1", request);

		verify(generator).generate(
				eq(WritingTaskType.COMPOSE),
				eq(List.of("past perfect", "article usage", "collocation: make/do")),
				eq("B1"), any());
		assertThat(dto.getPracticeItemId()).isEqualTo(7L);
		assertThat(dto.getSourceLang()).isEqualTo("vi");
		assertThat(dto.getTargetLang()).isEqualTo("en");
	}

	@Test
	void generateUsesExplicitFocusItemsInsteadOfWeakPoints() {
		when(generator.generate(any(), any(), any(), any()))
				.thenReturn(new GeneratedWritingPractice("Topic", "Dịch đoạn sau...", "Reference."));
		simulateGeneratedItemId(8L);

		GenerateWritingPracticeRequest request = new GenerateWritingPracticeRequest();
		request.setTaskType(WritingTaskType.TRANSLATE_VI_EN);
		request.setFocusItems(List.of("second conditional"));
		service.generate("user-1", request);

		verify(generator).generate(eq(WritingTaskType.TRANSLATE_VI_EN), eq(List.of("second conditional")), any(), any());
		verify(grammarWeakPointService, never()).getTopWeakPoints(any(), org.mockito.ArgumentMatchers.anyInt());
	}

	@Test
	void generatedItemDtoNeverCarriesTheReferenceAnswer() {
		when(generator.generate(any(), any(), any(), any()))
				.thenReturn(new GeneratedWritingPractice("Topic", "Dịch đoạn sau...", "SECRET reference translation"));
		simulateGeneratedItemId(9L);

		GenerateWritingPracticeRequest request = new GenerateWritingPracticeRequest();
		request.setTaskType(WritingTaskType.TRANSLATE_EN_VI);
		WritingPracticeItemDto dto = service.generate("user-1", request);

		// WritingPracticeItemDto has no reference-answer field at all; assert the serialized form the
		// client would receive really cannot contain it.
		assertThat(toJson(dto)).doesNotContain("SECRET reference translation");
	}

	@Test
	void submitComputesOverallScoreFromCriteriaAndRoutesEachErrorToItsOwnCategory() {
		when(mapper.findItemById(20L)).thenReturn(item(20L, WritingTaskType.COMPOSE, "Brief", "Model answer."));
		when(grader.grade(any(), any(), any(), any())).thenReturn(new WritingGrade(
				WritingCriteriaScores.builder().grammar(0.4).vocabulary(0.6).coherence(0.8).taskResponse(1.0).build(),
				"Corrected text.",
				List.of(
						error("past perfect", "grammar"),
						error("collocation: make/do", "vocabulary")),
				"Bài viết khá ổn."));

		WritingAttemptResultDto result = service.submit(submitRequest(20L, "My text."));

		// Mean of the four populated criteria, not any figure the LLM might have claimed separately.
		assertThat(result.getOverallScore()).isEqualTo(0.7);
		assertThat(result.getReferenceAnswer()).isEqualTo("Model answer.");
		assertThat(result.getActionAdvice()).hasSize(2);

		ArgumentCaptor<PracticeRedoRequest> captor = ArgumentCaptor.forClass(PracticeRedoRequest.class);
		verify(practiceService).redo(captor.capture());
		List<PracticeAttemptRequest> attempts = captor.getValue().getAttempts();
		assertThat(attempts).hasSize(2);
		assertThat(attempts).allSatisfy(attempt -> assertThat(attempt.isCorrect()).isFalse());
		assertThat(attempts.get(0).getCategory()).isEqualTo("grammar");
		assertThat(attempts.get(1).getCategory()).isEqualTo("vocabulary");
	}

	@Test
	void submitKeysItemIdsOnTheExistingPerCategoryPrefixesSoWeakPointsMergeRatherThanFork() {
		when(mapper.findItemById(21L)).thenReturn(item(21L, WritingTaskType.COMPOSE, "Brief", null));
		when(grader.grade(any(), any(), any(), any())).thenReturn(new WritingGrade(
				WritingCriteriaScores.builder().grammar(1.0).vocabulary(1.0).coherence(1.0).taskResponse(1.0).build(),
				"Corrected.",
				List.of(error("Past Perfect", "grammar"), error("Collocation: make/do", "vocabulary")),
				"ok"));

		service.submit(submitRequest(21L, "My text."));

		ArgumentCaptor<PracticeRedoRequest> captor = ArgumentCaptor.forClass(PracticeRedoRequest.class);
		verify(practiceService).redo(captor.capture());
		// vocabulary's existing rows are keyed "vocab:", NOT "vocabulary:" - deriving the prefix from
		// the category name would silently create a parallel set of weak points.
		assertThat(captor.getValue().getAttempts()).extracting(PracticeAttemptRequest::getItemId)
				.containsExactly("grammar:past perfect", "vocab:collocation: make/do");
	}

	@Test
	void submitDedupesRepeatsOfTheSameLabelWithinOneSubmission() {
		when(mapper.findItemById(22L)).thenReturn(item(22L, WritingTaskType.COMPOSE, "Brief", null));
		when(grader.grade(any(), any(), any(), any())).thenReturn(new WritingGrade(
				WritingCriteriaScores.builder().grammar(0.5).vocabulary(0.5).coherence(0.5).taskResponse(0.5).build(),
				"Corrected.",
				List.of(error("past perfect", "grammar"), error("past perfect", "grammar"), error("PAST PERFECT", "grammar")),
				"ok"));

		service.submit(submitRequest(22L, "My text."));

		ArgumentCaptor<PracticeRedoRequest> captor = ArgumentCaptor.forClass(PracticeRedoRequest.class);
		verify(practiceService).redo(captor.capture());
		assertThat(captor.getValue().getAttempts()).hasSize(1);
	}

	@Test
	void submitDoesNotTouchThePracticePipelineWhenThereAreNoErrors() {
		when(mapper.findItemById(23L)).thenReturn(item(23L, WritingTaskType.TRANSLATE_VI_EN, "Passage", "Reference."));
		when(grader.grade(any(), any(), any(), any())).thenReturn(new WritingGrade(
				WritingCriteriaScores.builder().grammar(1.0).vocabulary(1.0).coherence(1.0).accuracy(1.0).build(),
				"Same as submitted.", List.of(), "Rất tốt!"));

		WritingAttemptResultDto result = service.submit(submitRequest(23L, "Perfect translation."));

		assertThat(result.getOverallScore()).isEqualTo(1.0);
		assertThat(result.getActionAdvice()).isEmpty();
		verify(practiceService, never()).redo(any());
	}

	@Test
	void submitSkipsErrorsWhoseCategoryHasNoWeakPointDomain() {
		when(mapper.findItemById(24L)).thenReturn(item(24L, WritingTaskType.COMPOSE, "Brief", null));
		when(grader.grade(any(), any(), any(), any())).thenReturn(new WritingGrade(
				WritingCriteriaScores.builder().grammar(0.5).vocabulary(0.5).coherence(0.5).taskResponse(0.5).build(),
				"Corrected.",
				List.of(error("coherence between paragraphs", "coherence"), error("past perfect", "grammar")),
				"ok"));

		service.submit(submitRequest(24L, "My text."));

		ArgumentCaptor<PracticeRedoRequest> captor = ArgumentCaptor.forClass(PracticeRedoRequest.class);
		verify(practiceService).redo(captor.capture());
		assertThat(captor.getValue().getAttempts()).extracting(PracticeAttemptRequest::getCategory)
				.containsExactly("grammar");
	}

	@Test
	void submitPersistsTheGradersCriteriaAndErrorsSoHistoryNeverRegrades() {
		when(mapper.findItemById(25L)).thenReturn(item(25L, WritingTaskType.COMPOSE, "Brief", null));
		when(grader.grade(any(), any(), any(), any())).thenReturn(new WritingGrade(
				WritingCriteriaScores.builder().grammar(0.5).vocabulary(0.5).coherence(0.5).taskResponse(0.5).build(),
				"Corrected.", List.of(error("past perfect", "grammar")), "Nhận xét."));

		service.submit(submitRequest(25L, "My text."));

		ArgumentCaptor<WritingAttempt> captor = ArgumentCaptor.forClass(WritingAttempt.class);
		verify(mapper).insertAttempt(captor.capture());
		WritingAttempt saved = captor.getValue();
		assertThat(saved.getErrorsJson()).contains("past perfect").contains("grammar");
		assertThat(saved.getCriteriaJson()).contains("taskResponse");
		assertThat(saved.getFeedback()).isEqualTo("Nhận xét.");
		assertThat(saved.getOverallScore()).isEqualTo(0.5);
	}

	@Test
	void suggestNeverPassesTheReferenceAnswerToTheSuggester() {
		when(mapper.findItemById(30L)).thenReturn(
				item(30L, WritingTaskType.TRANSLATE_VI_EN, "Dịch đoạn sau...", "SECRET reference translation"));
		when(suggester.suggest(any(), any(), any(), any()))
				.thenReturn(List.of(WritingSuggestion.builder().ideaVi("Dịch câu thứ hai.").build()));

		SuggestNextSentenceRequest request = new SuggestNextSentenceRequest();
		request.setPracticeItemId(30L);
		request.setDraftText("I have lived here...");
		List<WritingSuggestion> suggestions = service.suggest(request);

		assertThat(suggestions).hasSize(1);
		// The suggester's signature has no reference-answer parameter; assert the prompt text passed
		// in is the source passage, so the model translation cannot leak into a hint.
		verify(suggester).suggest(
				eq(WritingTaskType.TRANSLATE_VI_EN), eq("Dịch đoạn sau..."), eq("I have lived here..."), any());
	}

	@Test
	void generatePracticeFromAttemptTargetsThatAttemptsOwnMistakeLabels() {
		when(mapper.findAttemptDetailByIdAndUserId(31L, "user-1")).thenReturn(WritingAttemptDetailRow.builder()
				.attemptId(31L).taskType(WritingTaskType.TRANSLATE_EN_VI).level("B2").examType("IELTS")
				.errorsJson(toJson(List.of(error("past perfect", "grammar"), error("word form: advise/advice", "vocabulary"))))
				.build());
		when(generator.generate(any(), any(), any(), any()))
				.thenReturn(new GeneratedWritingPractice("Retry", "Dịch đoạn sau...", "Reference."));
		when(mapper.findItemsByUserId("user-1")).thenReturn(List.of());
		simulateGeneratedItemId(40L);

		service.generatePracticeFromAttempt("user-1", 31L, null);

		verify(generator).generate(
				eq(WritingTaskType.TRANSLATE_EN_VI),
				eq(List.of("past perfect", "word form: advise/advice")),
				eq("B2"), eq("IELTS"));
	}

	@Test
	void generatePracticeFromAttemptRejectsAnotherLearnersAttempt() {
		when(mapper.findAttemptDetailByIdAndUserId(99L, "user-1")).thenReturn(null);

		assertThatThrownBy(() -> service.generatePracticeFromAttempt("user-1", 99L, null))
				.isInstanceOf(BusinessException.class);
	}

	@Test
	void getItemRejectsAnUnknownId() {
		when(mapper.findItemById(404L)).thenReturn(null);

		assertThatThrownBy(() -> service.getItem(404L)).isInstanceOf(BusinessException.class);
	}

	// --- helpers ---

	private WritingPracticeItem item(Long id, WritingTaskType taskType, String promptText, String referenceAnswer) {
		return WritingPracticeItem.builder()
				.id(id).userId("user-1").taskType(taskType).level("B1")
				.promptText(promptText).referenceAnswer(referenceAnswer)
				.sourceLang(taskType.sourceLang()).targetLang(taskType.targetLang())
				.build();
	}

	private SubmitWritingAttemptRequest submitRequest(Long itemId, String text) {
		SubmitWritingAttemptRequest request = new SubmitWritingAttemptRequest();
		request.setUserId("user-1");
		request.setPracticeItemId(itemId);
		request.setSubmittedText(text);
		return request;
	}

	private WritingErrorItem error(String label, String category) {
		return WritingErrorItem.builder()
				.wrong("wrong span").corrected("corrected span")
				.label(label).category(category)
				.explanationVi("Giải thích.").severity("major")
				.build();
	}

	private GrammarWeakPoint weakGrammar(String label) {
		return GrammarWeakPoint.builder().label(label).build();
	}

	private VocabularyWeakPoint weakVocabulary(String label) {
		return VocabularyWeakPoint.builder().label(label).build();
	}

	private void simulateGeneratedItemId(Long id) {
		doAnswer(invocation -> {
			WritingPracticeItem item = invocation.getArgument(0);
			item.setId(id);
			return null;
		}).when(mapper).insertItem(any(WritingPracticeItem.class));
	}

	private String toJson(Object value) {
		try {
			return objectMapper.writeValueAsString(value);
		} catch (Exception ex) {
			throw new RuntimeException(ex);
		}
	}
}
