package com.remelearning.english.vocabulary.learn.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.remelearning.common.exception.BusinessException;
import com.remelearning.english.practice.dto.PracticeRedoRequest;
import com.remelearning.english.practice.service.PracticeService;
import com.remelearning.english.vocabulary.domain.VocabularyType;
import com.remelearning.english.vocabulary.domain.VocabularyWeakPoint;
import com.remelearning.english.vocabulary.learn.domain.VocabAttemptDetailRow;
import com.remelearning.english.vocabulary.learn.domain.VocabPracticeItem;
import com.remelearning.english.vocabulary.learn.domain.VocabQuestionItem;
import com.remelearning.english.vocabulary.learn.domain.VocabQuestionType;
import com.remelearning.english.vocabulary.learn.dto.GenerateVocabPracticeRequest;
import com.remelearning.english.vocabulary.learn.dto.SubmitVocabAttemptRequest;
import com.remelearning.english.vocabulary.learn.dto.VocabAttemptDetailDto;
import com.remelearning.english.vocabulary.learn.dto.VocabAttemptResultDto;
import com.remelearning.english.vocabulary.learn.dto.VocabPracticeItemDto;
import com.remelearning.english.vocabulary.learn.generator.GeneratedVocabPractice;
import com.remelearning.english.vocabulary.learn.generator.VocabPracticeGenerator;
import com.remelearning.english.vocabulary.learn.mapper.VocabPracticeMapper;
import com.remelearning.english.vocabulary.service.VocabularyWeakPointService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class VocabLearnServiceImplTest {

	private final VocabPracticeMapper mapper = mock(VocabPracticeMapper.class);
	private final VocabPracticeGenerator generator = mock(VocabPracticeGenerator.class);
	private final VocabularyWeakPointService weakPointService = mock(VocabularyWeakPointService.class);
	private final PracticeService practiceService = mock(PracticeService.class);
	private final ObjectMapper objectMapper = new ObjectMapper();
	private final VocabLearnServiceImpl service =
			new VocabLearnServiceImpl(mapper, generator, weakPointService, practiceService, objectMapper);

	@Test
	void generateFallsBackToTopWeakPointsWhenNoFocusItemsGiven() {
		when(weakPointService.getTopWeakPoints(eq("user-1"), anyInt())).thenReturn(List.of(
				VocabularyWeakPoint.builder().label("reluctant").vocabularyType(VocabularyType.ADJECTIVE).build()));
		when(generator.generate(eq(List.of("reluctant")), any(), any())).thenReturn(new GeneratedVocabPractice(
				"Vocabulary", List.of(VocabQuestionItem.builder()
						.targetWord("reluctant").type(VocabQuestionType.CLOZE)
						.prompt("She was ____ to admit it.").answer("reluctant").translation("miễn cưỡng").build())));
		simulateGeneratedItemId(10L);

		VocabPracticeItemDto dto = service.generate("user-1", new GenerateVocabPracticeRequest());

		assertThat(dto.getPracticeItemId()).isEqualTo(10L);
		assertThat(dto.getTargetWords()).containsExactly("reluctant");
		assertThat(dto.getQuestions()).hasSize(1);
		// The answer must never leak to the client before grading.
		assertThat(dto.getQuestions().get(0).getPrompt()).isEqualTo("She was ____ to admit it.");
	}

	@Test
	void generateUsesExplicitFocusItemsInsteadOfWeakPoints() {
		when(generator.generate(eq(List.of("brief")), any(), any()))
				.thenReturn(new GeneratedVocabPractice("Vocabulary", List.of(
						VocabQuestionItem.builder().targetWord("brief").type(VocabQuestionType.CLOZE).answer("brief").build())));
		simulateGeneratedItemId(11L);

		GenerateVocabPracticeRequest request = new GenerateVocabPracticeRequest();
		request.setFocusItems(List.of("brief"));

		service.generate("user-1", request);

		verify(weakPointService, never()).getTopWeakPoints(any(), anyInt());
	}

	@Test
	void submitScoresGradesAndFeedsWeakPointPipeline() {
		VocabPracticeItem item = VocabPracticeItem.builder()
				.id(20L).userId("user-1")
				.targetWordsJson("[\"reluctant\",\"brief\"]")
				.itemsJson(toJson(List.of(
						VocabQuestionItem.builder().targetWord("reluctant").type(VocabQuestionType.CLOZE)
								.prompt("p1").answer("reluctant").translation("miễn cưỡng").build(),
						VocabQuestionItem.builder().targetWord("brief").type(VocabQuestionType.MCQ)
								.prompt("p2").options(List.of("brief", "long")).answer("brief").build())))
				.build();
		when(mapper.findItemById(20L)).thenReturn(item);

		SubmitVocabAttemptRequest request = new SubmitVocabAttemptRequest();
		request.setUserId("user-1");
		request.setPracticeItemId(20L);
		request.setAnswers(List.of("wrong", "brief"));

		VocabAttemptResultDto result = service.submit(request);

		assertThat(result.getAccuracy()).isEqualTo(0.5);
		assertThat(result.getResults()).extracting(r -> r.isCorrect()).containsExactly(false, true);
		assertThat(result.getActionAdvice()).anyMatch(advice -> advice.contains("reluctant"));

		ArgumentCaptor<PracticeRedoRequest> redoCaptor = ArgumentCaptor.forClass(PracticeRedoRequest.class);
		verify(practiceService).redo(redoCaptor.capture());
		assertThat(redoCaptor.getValue().getUserId()).isEqualTo("user-1");
		assertThat(redoCaptor.getValue().getAttempts()).hasSize(2);
		assertThat(redoCaptor.getValue().getAttempts().get(0).getCategory()).isEqualTo("vocabulary");
		assertThat(redoCaptor.getValue().getAttempts().get(0).isCorrect()).isFalse();
		assertThat(redoCaptor.getValue().getAttempts().get(1).isCorrect()).isTrue();
	}

	@Test
	void getItemThrowsNotFoundForUnknownId() {
		when(mapper.findItemById(99L)).thenReturn(null);

		assertThatThrownBy(() -> service.getItem(99L)).isInstanceOf(BusinessException.class);
	}

	@Test
	void getAttemptDetailRebuildsResultsFromStoredAnswers() {
		VocabAttemptDetailRow row = VocabAttemptDetailRow.builder()
				.attemptId(30L).level("B1").examType("TOEIC").topic("Vocabulary")
				.itemsJson(toJson(List.of(
						VocabQuestionItem.builder().targetWord("brief").type(VocabQuestionType.CLOZE).answer("brief").build())))
				.answersJson("[\"brief\"]")
				.score(1.0)
				.createdAt(Instant.now())
				.build();
		when(mapper.findAttemptDetailByIdAndUserId(30L, "user-1")).thenReturn(row);

		VocabAttemptDetailDto dto = service.getAttemptDetail("user-1", 30L);

		assertThat(dto.getResults()).hasSize(1);
		assertThat(dto.getResults().get(0).isCorrect()).isTrue();
	}

	private void simulateGeneratedItemId(Long id) {
		org.mockito.Mockito.doAnswer(invocation -> {
			VocabPracticeItem item = invocation.getArgument(0);
			item.setId(id);
			return null;
		}).when(mapper).insertItem(any(VocabPracticeItem.class));
	}

	private String toJson(Object value) {
		try {
			return objectMapper.writeValueAsString(value);
		} catch (Exception ex) {
			throw new RuntimeException(ex);
		}
	}
}
