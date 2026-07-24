package com.remelearning.english.grammar.learn.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.remelearning.common.exception.BusinessException;
import com.remelearning.english.grammar.domain.GrammarType;
import com.remelearning.english.grammar.domain.GrammarWeakPoint;
import com.remelearning.english.grammar.learn.domain.GrammarAttemptDetailRow;
import com.remelearning.english.grammar.learn.domain.GrammarPracticeItem;
import com.remelearning.english.grammar.learn.domain.GrammarQuestionItem;
import com.remelearning.english.grammar.learn.domain.GrammarQuestionType;
import com.remelearning.english.grammar.learn.dto.GenerateGrammarPracticeRequest;
import com.remelearning.english.grammar.learn.dto.SubmitGrammarAttemptRequest;
import com.remelearning.english.grammar.learn.dto.GrammarAttemptDetailDto;
import com.remelearning.english.grammar.learn.dto.GrammarAttemptResultDto;
import com.remelearning.english.grammar.learn.dto.GrammarPracticeItemDto;
import com.remelearning.english.grammar.learn.generator.GeneratedGrammarPractice;
import com.remelearning.english.grammar.learn.generator.GrammarPracticeGenerator;
import com.remelearning.english.grammar.learn.mapper.GrammarPracticeMapper;
import com.remelearning.english.grammar.service.GrammarWeakPointService;
import com.remelearning.english.practice.dto.PracticeRedoRequest;
import com.remelearning.english.practice.service.PracticeService;
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

class GrammarLearnServiceImplTest {

	private final GrammarPracticeMapper mapper = mock(GrammarPracticeMapper.class);
	private final GrammarPracticeGenerator generator = mock(GrammarPracticeGenerator.class);
	private final GrammarWeakPointService weakPointService = mock(GrammarWeakPointService.class);
	private final PracticeService practiceService = mock(PracticeService.class);
	private final ObjectMapper objectMapper = new ObjectMapper();
	private final GrammarLearnServiceImpl service =
			new GrammarLearnServiceImpl(mapper, generator, weakPointService, practiceService, objectMapper);

	@Test
	void generateFallsBackToTopWeakPointsWhenNoFocusItemsGiven() {
		when(weakPointService.getTopWeakPoints(eq("user-1"), anyInt())).thenReturn(List.of(
				GrammarWeakPoint.builder().label("past perfect").grammarType(GrammarType.TENSE).build()));
		when(generator.generate(eq(List.of("past perfect")), any(), any())).thenReturn(new GeneratedGrammarPractice(
				"Grammar", List.of(GrammarQuestionItem.builder()
						.targetRule("past perfect").type(GrammarQuestionType.FILL_TENSE)
						.prompt("She (already leave) when I arrived.").answer("had already left").translation("thi qua khu hoan thanh").build())));
		simulateGeneratedItemId(10L);

		GrammarPracticeItemDto dto = service.generate("user-1", new GenerateGrammarPracticeRequest());

		assertThat(dto.getPracticeItemId()).isEqualTo(10L);
		assertThat(dto.getTargetRules()).containsExactly("past perfect");
		assertThat(dto.getQuestions()).hasSize(1);
	}

	@Test
	void generateUsesExplicitFocusItemsInsteadOfWeakPoints() {
		when(generator.generate(eq(List.of("passive voice")), any(), any()))
				.thenReturn(new GeneratedGrammarPractice("Grammar", List.of(
						GrammarQuestionItem.builder().targetRule("passive voice").type(GrammarQuestionType.TRANSFORM).answer("x").build())));
		simulateGeneratedItemId(11L);

		GenerateGrammarPracticeRequest request = new GenerateGrammarPracticeRequest();
		request.setFocusItems(List.of("passive voice"));

		service.generate("user-1", request);

		verify(weakPointService, never()).getTopWeakPoints(any(), anyInt());
	}

	@Test
	void submitScoresGradesAndFeedsWeakPointPipeline() {
		GrammarPracticeItem item = GrammarPracticeItem.builder()
				.id(20L).userId("user-1")
				.targetRulesJson("[\"past perfect\",\"passive voice\"]")
				.itemsJson(toJson(List.of(
						GrammarQuestionItem.builder().targetRule("past perfect").type(GrammarQuestionType.FILL_TENSE)
								.prompt("p1").answer("had left").translation("giai thich").build(),
						GrammarQuestionItem.builder().targetRule("passive voice").type(GrammarQuestionType.MCQ)
								.prompt("p2").options(List.of("was cooked", "cooked")).answer("was cooked").build())))
				.build();
		when(mapper.findItemById(20L)).thenReturn(item);

		SubmitGrammarAttemptRequest request = new SubmitGrammarAttemptRequest();
		request.setUserId("user-1");
		request.setPracticeItemId(20L);
		request.setAnswers(List.of("wrong", "was cooked"));

		GrammarAttemptResultDto result = service.submit(request);

		assertThat(result.getAccuracy()).isEqualTo(0.5);
		assertThat(result.getResults()).extracting(r -> r.isCorrect()).containsExactly(false, true);
		assertThat(result.getActionAdvice()).anyMatch(advice -> advice.contains("past perfect"));

		ArgumentCaptor<PracticeRedoRequest> redoCaptor = ArgumentCaptor.forClass(PracticeRedoRequest.class);
		verify(practiceService).redo(redoCaptor.capture());
		assertThat(redoCaptor.getValue().getUserId()).isEqualTo("user-1");
		assertThat(redoCaptor.getValue().getAttempts()).hasSize(2);
		assertThat(redoCaptor.getValue().getAttempts().get(0).getCategory()).isEqualTo("grammar");
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
		GrammarAttemptDetailRow row = GrammarAttemptDetailRow.builder()
				.attemptId(30L).level("B1").examType("TOEIC").topic("Grammar")
				.itemsJson(toJson(List.of(
						GrammarQuestionItem.builder().targetRule("passive voice").type(GrammarQuestionType.TRANSFORM).answer("was cooked").build())))
				.answersJson("[\"was cooked\"]")
				.score(1.0)
				.createdAt(Instant.now())
				.build();
		when(mapper.findAttemptDetailByIdAndUserId(30L, "user-1")).thenReturn(row);

		GrammarAttemptDetailDto dto = service.getAttemptDetail("user-1", 30L);

		assertThat(dto.getResults()).hasSize(1);
		assertThat(dto.getResults().get(0).isCorrect()).isTrue();
	}

	@Test
	void generatePracticeFromAttemptThrowsNotFoundWhenAttemptDoesNotBelongToUser() {
		when(mapper.findAttemptDetailByIdAndUserId(30L, "user-1")).thenReturn(null);

		assertThatThrownBy(() -> service.generatePracticeFromAttempt("user-1", 30L))
				.isInstanceOf(BusinessException.class);
	}

	@Test
	void generatePracticeFromAttemptGeneratesTargetingMissedRulesAndReturnsRefreshedList() {
		GrammarAttemptDetailRow attempt = GrammarAttemptDetailRow.builder()
				.attemptId(30L).level("B1").examType("TOEIC").topic("Grammar")
				.itemsJson(toJson(List.of(
						GrammarQuestionItem.builder().targetRule("past perfect").type(GrammarQuestionType.FILL_TENSE)
								.prompt("p1").answer("had left").build(),
						GrammarQuestionItem.builder().targetRule("passive voice").type(GrammarQuestionType.MCQ)
								.prompt("p2").answer("was cooked").build())))
				.answersJson(toJson(List.of("wrong", "was cooked")))
				.score(0.5)
				.createdAt(Instant.now())
				.build();
		when(mapper.findAttemptDetailByIdAndUserId(30L, "user-1")).thenReturn(attempt);
		when(generator.generate(eq(List.of("past perfect")), eq("B1"), eq("TOEIC"))).thenReturn(new GeneratedGrammarPractice(
				"Past perfect practice", List.of(GrammarQuestionItem.builder()
						.targetRule("past perfect").type(GrammarQuestionType.FILL_TENSE)
						.prompt("She (already leave).").answer("had already left").build())));
		simulateGeneratedItemId(40L);
		when(mapper.findItemsByUserId("user-1")).thenReturn(List.of(
				GrammarPracticeItem.builder().id(40L).userId("user-1").level("B1").examType("TOEIC")
						.topic("Past perfect practice").targetRulesJson(toJson(List.of("past perfect")))
						.itemsJson(toJson(List.of(GrammarQuestionItem.builder().targetRule("past perfect")
								.type(GrammarQuestionType.FILL_TENSE).prompt("She (already leave).")
								.answer("had already left").build())))
						.build()));

		List<GrammarPracticeItemDto> result = service.generatePracticeFromAttempt("user-1", 30L);

		verify(generator).generate(List.of("past perfect"), "B1", "TOEIC");
		assertThat(result).hasSize(1);
		assertThat(result.get(0).getPracticeItemId()).isEqualTo(40L);
	}

	private void simulateGeneratedItemId(Long id) {
		org.mockito.Mockito.doAnswer(invocation -> {
			GrammarPracticeItem item = invocation.getArgument(0);
			item.setId(id);
			return null;
		}).when(mapper).insertItem(any(GrammarPracticeItem.class));
	}

	private String toJson(Object value) {
		try {
			return objectMapper.writeValueAsString(value);
		} catch (Exception ex) {
			throw new RuntimeException(ex);
		}
	}
}
