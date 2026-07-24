package com.remelearning.english.grammar.library.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.remelearning.common.exception.BusinessException;
import com.remelearning.english.grammar.learn.domain.GrammarQuestionType;
import com.remelearning.english.grammar.library.domain.GrammarLibraryContent;
import com.remelearning.english.grammar.library.domain.GrammarLibraryExample;
import com.remelearning.english.grammar.library.domain.GrammarLibraryQuestion;
import com.remelearning.english.grammar.library.domain.GrammarLibrarySession;
import com.remelearning.english.grammar.library.domain.GrammarLibrarySessionAnswer;
import com.remelearning.english.grammar.library.domain.GrammarLibrarySessionQuestion;
import com.remelearning.english.grammar.library.domain.GrammarLibraryTopic;
import com.remelearning.english.grammar.library.domain.GrammarSessionStatus;
import com.remelearning.english.grammar.library.domain.GrammarSessionType;
import com.remelearning.english.grammar.library.domain.GrammarTopicProgress;
import com.remelearning.english.grammar.library.domain.GrammarTopicStatus;
import com.remelearning.english.grammar.library.dto.FinishGrammarLibrarySessionResponse;
import com.remelearning.english.grammar.library.dto.GrammarLibraryAnswerResultDto;
import com.remelearning.english.grammar.library.dto.GrammarLibraryContentDto;
import com.remelearning.english.grammar.library.dto.StartGrammarSessionResponse;
import com.remelearning.english.grammar.library.dto.SubmitGrammarLibraryAnswerRequest;
import com.remelearning.english.grammar.library.generator.GeneratedGrammarTopicContent;
import com.remelearning.english.grammar.library.generator.GrammarLibraryContentGenerator;
import com.remelearning.english.grammar.library.generator.GrammarLibraryQuestionSeed;
import com.remelearning.english.grammar.library.mapper.GrammarLibraryContentMapper;
import com.remelearning.english.grammar.library.mapper.GrammarLibraryQuestionMapper;
import com.remelearning.english.grammar.library.mapper.GrammarLibrarySessionMapper;
import com.remelearning.english.grammar.library.mapper.GrammarLibraryTopicMapper;
import com.remelearning.english.grammar.library.mapper.GrammarTopicProgressMapper;
import com.remelearning.english.grammar.learn.dto.GrammarPracticeItemDto;
import com.remelearning.english.grammar.learn.service.GrammarLearnService;
import com.remelearning.english.practice.dto.PracticeRedoRequest;
import com.remelearning.english.practice.service.PracticeService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class GrammarLibraryServiceImplTest {

	private final GrammarLibraryTopicMapper topicMapper = mock(GrammarLibraryTopicMapper.class);
	private final GrammarLibraryContentMapper contentMapper = mock(GrammarLibraryContentMapper.class);
	private final GrammarLibraryQuestionMapper questionMapper = mock(GrammarLibraryQuestionMapper.class);
	private final GrammarTopicProgressMapper progressMapper = mock(GrammarTopicProgressMapper.class);
	private final GrammarLibrarySessionMapper sessionMapper = mock(GrammarLibrarySessionMapper.class);
	private final GrammarLibraryContentGenerator generator = mock(GrammarLibraryContentGenerator.class);
	private final PracticeService practiceService = mock(PracticeService.class);
	private final ObjectMapper objectMapper = new ObjectMapper();
	private final GrammarLearnService grammarLearnService = mock(GrammarLearnService.class);
	private final GrammarLibraryServiceImpl service = new GrammarLibraryServiceImpl(
			topicMapper, contentMapper, questionMapper, progressMapper, sessionMapper, generator, practiceService, objectMapper,
			grammarLearnService);

	private GrammarLibraryTopic topic() {
		return GrammarLibraryTopic.builder().id(1L).code("present_simple").name("Present Simple")
				.level("beginner").sequenceOrder(1).build();
	}

	@Test
	void getTopicContentGeneratesAndPersistsOnFirstRead() {
		when(topicMapper.findById(1L)).thenReturn(topic());
		when(contentMapper.findByTopicId(1L)).thenReturn(null);
		when(generator.generateTopicContent("Present Simple", "beginner")).thenReturn(new GeneratedGrammarTopicContent(
				"explanation en", "giai thich", "S + V(s/es) + O",
				List.of(GrammarLibraryExample.builder().en("She works.").vi("Co ay lam viec.").build()),
				List.of(new GrammarLibraryQuestionSeed(GrammarQuestionType.MCQ, "She ___ every day.",
						List.of("work", "works"), "works", "third person singular", "Cô ấy làm việc mỗi ngày."))));
		when(questionMapper.findByTopicId(1L)).thenReturn(List.of(GrammarLibraryQuestion.builder()
				.id(5L).topicId(1L).questionType(GrammarQuestionType.MCQ).prompt("She ___ every day.")
				.optionsJson("[\"work\",\"works\"]").answer("works").explanationVi("third person singular").build()));

		GrammarLibraryContentDto dto = service.getTopicContent(1L);

		verify(contentMapper).insert(any(GrammarLibraryContent.class));
		verify(questionMapper).insert(any(GrammarLibraryQuestion.class));
		assertThat(dto.getExplanationEn()).isEqualTo("explanation en");
		assertThat(dto.getQuestions()).hasSize(1);
		assertThat(dto.getQuestions().get(0).getAnswer()).isEqualTo("works");
	}

	@Test
	void getTopicContentReusesPersistedContentWithoutCallingGeneratorAgain() {
		when(topicMapper.findById(1L)).thenReturn(topic());
		when(contentMapper.findByTopicId(1L)).thenReturn(GrammarLibraryContent.builder()
				.id(9L).topicId(1L).explanationEn("en").explanationVi("vi").illustrationText("S+V")
				.examplesJson("[]").build());
		when(questionMapper.findByTopicId(1L)).thenReturn(List.of());

		service.getTopicContent(1L);

		verify(generator, never()).generateTopicContent(anyString(), any());
		verify(contentMapper, never()).insert(any());
	}

	@Test
	void startSessionThrowsWhenTopicIsLocked() {
		when(topicMapper.findById(1L)).thenReturn(topic());
		when(progressMapper.findByUserIdAndTopicId("user-1", 1L)).thenReturn(
				GrammarTopicProgress.builder().userId("user-1").topicId(1L).status(GrammarTopicStatus.LOCKED).build());

		assertThatThrownBy(() -> service.startSession("user-1", 1L)).isInstanceOf(BusinessException.class);
	}

	@Test
	void startSessionBuildsInitialSessionFromPoolAndMarksInProgress() {
		when(topicMapper.findById(1L)).thenReturn(topic());
		when(progressMapper.findByUserIdAndTopicId("user-1", 1L)).thenReturn(
				GrammarTopicProgress.builder().userId("user-1").topicId(1L).status(GrammarTopicStatus.UNLOCKED).build());
		when(contentMapper.findByTopicId(1L)).thenReturn(GrammarLibraryContent.builder().id(1L).topicId(1L).build());
		when(questionMapper.findByTopicId(1L)).thenReturn(List.of(
				GrammarLibraryQuestion.builder().id(5L).topicId(1L).questionType(GrammarQuestionType.MCQ)
						.prompt("p1").optionsJson("[\"a\",\"b\"]").answer("a").explanationVi("x").build()));
		simulateGeneratedSessionId(100L);

		StartGrammarSessionResponse response = service.startSession("user-1", 1L);

		assertThat(response.getSessionId()).isEqualTo(100L);
		assertThat(response.getSessionType()).isEqualTo(GrammarSessionType.INITIAL);
		assertThat(response.getQuestions()).hasSize(1);
		assertThat(response.getQuestions().get(0).getQuestionRef()).isEqualTo("q-5");
		verify(progressMapper).markInProgress("user-1", 1L);
	}

	@Test
	void submitAnswerGradesCorrectlyAgainstStoredQuestion() {
		GrammarLibrarySession session = GrammarLibrarySession.builder()
				.id(200L).userId("user-1").topicId(1L).status(GrammarSessionStatus.IN_PROGRESS)
				.questionsJson(toJson(List.of(GrammarLibrarySessionQuestion.builder()
						.questionRef("q-5").type(GrammarQuestionType.MCQ).prompt("p1").answer("works").explanationVi("x").build())))
				.build();
		when(sessionMapper.findById(200L)).thenReturn(session);

		SubmitGrammarLibraryAnswerRequest request = new SubmitGrammarLibraryAnswerRequest();
		request.setQuestionRef("q-5");
		request.setSubmittedAnswer("works");

		GrammarLibraryAnswerResultDto result = service.submitAnswer(200L, request);

		assertThat(result.isCorrect()).isTrue();
		assertThat(result.getCorrectAnswer()).isEqualTo("works");
		verify(sessionMapper).insertAnswer(any());
	}

	@Test
	void finishSessionPassesTopicAndUnlocksNextWhenAllCorrect() {
		GrammarLibrarySession session = GrammarLibrarySession.builder()
				.id(300L).userId("user-1").topicId(1L).status(GrammarSessionStatus.IN_PROGRESS)
				.questionsJson(toJson(List.of(GrammarLibrarySessionQuestion.builder()
						.questionRef("q-5").type(GrammarQuestionType.MCQ).prompt("p1").answer("works").explanationVi("x").build())))
				.build();
		when(sessionMapper.findById(300L)).thenReturn(session);
		when(sessionMapper.findAnswersBySessionId(300L)).thenReturn(List.of(
				GrammarLibrarySessionAnswer.builder().sessionId(300L).questionRef("q-5").submittedAnswer("works").correct(true).build()));
		when(topicMapper.findById(1L)).thenReturn(topic());
		GrammarLibraryTopic nextTopic = GrammarLibraryTopic.builder().id(2L).code("present_continuous")
				.name("Present Continuous").level("beginner").sequenceOrder(2).build();
		when(topicMapper.findBySequenceOrder(2)).thenReturn(nextTopic);
		when(progressMapper.findByUserIdAndTopicId("user-1", 2L)).thenReturn(
				GrammarTopicProgress.builder().userId("user-1").topicId(2L).status(GrammarTopicStatus.UNLOCKED).build());

		FinishGrammarLibrarySessionResponse response = service.finishSession(300L);

		assertThat(response.isPassed()).isTrue();
		assertThat(response.getRetrySession()).isNull();
		assertThat(response.isNextTopicUnlocked()).isTrue();
		assertThat(response.getNextTopicId()).isEqualTo(2L);
		verify(progressMapper).markPassed("user-1", 1L);
		verify(progressMapper).unlockIfLocked("user-1", 2L);

		ArgumentCaptor<PracticeRedoRequest> redoCaptor = ArgumentCaptor.forClass(PracticeRedoRequest.class);
		verify(practiceService).redo(redoCaptor.capture());
		assertThat(redoCaptor.getValue().getAttempts()).hasSize(1);
		assertThat(redoCaptor.getValue().getAttempts().get(0).isCorrect()).isTrue();
		assertThat(redoCaptor.getValue().getAttempts().get(0).getItemId()).isEqualTo("grammar:present_simple");
	}

	@Test
	void finishSessionBuildsRetrySessionWhenSomeAnswersAreWrong() {
		GrammarLibrarySession session = GrammarLibrarySession.builder()
				.id(400L).userId("user-1").topicId(1L).status(GrammarSessionStatus.IN_PROGRESS)
				.questionsJson(toJson(List.of(GrammarLibrarySessionQuestion.builder()
						.questionRef("q-5").type(GrammarQuestionType.MCQ).prompt("p1").answer("works").explanationVi("x").build())))
				.build();
		when(sessionMapper.findById(400L)).thenReturn(session);
		when(sessionMapper.findAnswersBySessionId(400L)).thenReturn(List.of(
				GrammarLibrarySessionAnswer.builder().sessionId(400L).questionRef("q-5").submittedAnswer("wrong").correct(false).build()));
		when(topicMapper.findById(1L)).thenReturn(topic());
		when(generator.generateRetryQuestion(eq("Present Simple"), eq("beginner"), eq(GrammarQuestionType.MCQ), eq("p1")))
				.thenReturn(new GrammarLibraryQuestionSeed(GrammarQuestionType.MCQ, "p2", List.of("a", "b"), "b", "y", "z"));
		simulateGeneratedSessionId(401L);

		FinishGrammarLibrarySessionResponse response = service.finishSession(400L);

		assertThat(response.isPassed()).isFalse();
		assertThat(response.getRetrySession()).isNotNull();
		assertThat(response.getRetrySession().getSessionType()).isEqualTo(GrammarSessionType.RETRY);
		assertThat(response.getRetrySession().getQuestions()).hasSize(1);
		assertThat(response.getRetrySession().getQuestions().get(0).getQuestionRef()).isEqualTo("r-0");
		verify(progressMapper, never()).markPassed(any(), any());
	}

	@Test
	void generatePracticeFromSessionThrowsNotFoundWhenSessionDoesNotBelongToUser() {
		when(sessionMapper.findById(300L)).thenReturn(GrammarLibrarySession.builder()
				.id(300L).userId("someone-else").topicId(1L).build());

		assertThatThrownBy(() -> service.generatePracticeFromSession("user-1", 300L))
				.isInstanceOf(BusinessException.class);
	}

	@Test
	void generatePracticeFromSessionThrowsNotFoundWhenSessionDoesNotExist() {
		when(sessionMapper.findById(300L)).thenReturn(null);

		assertThatThrownBy(() -> service.generatePracticeFromSession("user-1", 300L))
				.isInstanceOf(BusinessException.class);
	}

	@Test
	void generatePracticeFromSessionDelegatesTopicNameToGrammarLearnServiceWhenSomeAnswersAreWrong() {
		GrammarLibrarySession session = GrammarLibrarySession.builder()
				.id(300L).userId("user-1").topicId(1L).status(GrammarSessionStatus.COMPLETED)
				.questionsJson(toJson(List.of(GrammarLibrarySessionQuestion.builder()
						.questionRef("q-5").type(GrammarQuestionType.MCQ).prompt("She ___ every day.").answer("works").build())))
				.build();
		when(sessionMapper.findById(300L)).thenReturn(session);
		when(sessionMapper.findAnswersBySessionId(300L)).thenReturn(List.of(
				GrammarLibrarySessionAnswer.builder().sessionId(300L).questionRef("q-5").submittedAnswer("work").correct(false).build()));
		when(topicMapper.findById(1L)).thenReturn(topic());
		List<GrammarPracticeItemDto> refreshed = List.of(GrammarPracticeItemDto.builder().practiceItemId(40L).build());
		when(grammarLearnService.generatePracticeForRules("user-1", List.of("Present Simple"), "beginner", null))
				.thenReturn(refreshed);

		List<GrammarPracticeItemDto> result = service.generatePracticeFromSession("user-1", 300L);

		assertThat(result).isEqualTo(refreshed);
		verify(grammarLearnService).generatePracticeForRules("user-1", List.of("Present Simple"), "beginner", null);
	}

	@Test
	void generatePracticeFromSessionReturnsEmptyWithoutCallingGeneratorWhenAllAnswersCorrect() {
		GrammarLibrarySession session = GrammarLibrarySession.builder()
				.id(300L).userId("user-1").topicId(1L).status(GrammarSessionStatus.COMPLETED)
				.questionsJson(toJson(List.of(GrammarLibrarySessionQuestion.builder()
						.questionRef("q-5").type(GrammarQuestionType.MCQ).prompt("She ___ every day.").answer("works").build())))
				.build();
		when(sessionMapper.findById(300L)).thenReturn(session);
		when(sessionMapper.findAnswersBySessionId(300L)).thenReturn(List.of(
				GrammarLibrarySessionAnswer.builder().sessionId(300L).questionRef("q-5").submittedAnswer("works").correct(true).build()));

		List<GrammarPracticeItemDto> result = service.generatePracticeFromSession("user-1", 300L);

		assertThat(result).isEmpty();
		verify(topicMapper, never()).findById(any());
		verify(grammarLearnService, never()).generatePracticeForRules(any(), any(), any(), any());
	}

	@Test
	void getHistoryIncludesTopicIdForFeReopenNavigation() {
		GrammarLibrarySession session = GrammarLibrarySession.builder()
				.id(500L).userId("user-1").topicId(1L).sessionType(GrammarSessionType.INITIAL)
				.correctCount(4).totalCount(5).build();
		when(sessionMapper.findCompletedByUserIdAndTopicId("user-1", 1L)).thenReturn(List.of(session));

		List<com.remelearning.english.grammar.library.dto.GrammarLibraryHistoryEntryDto> history =
				service.getHistory("user-1", 1L);

		assertThat(history).hasSize(1);
		assertThat(history.get(0).getTopicId()).isEqualTo(1L);
		assertThat(history.get(0).getAccuracy()).isEqualTo(0.8);
	}

	@Test
	void getHistoryForUserReturnsSessionsAcrossAllTopics() {
		GrammarLibrarySession sessionTopic1 = GrammarLibrarySession.builder()
				.id(500L).userId("user-1").topicId(1L).sessionType(GrammarSessionType.INITIAL)
				.correctCount(4).totalCount(5).build();
		GrammarLibrarySession sessionTopic2 = GrammarLibrarySession.builder()
				.id(501L).userId("user-1").topicId(2L).sessionType(GrammarSessionType.INITIAL)
				.correctCount(3).totalCount(3).build();
		when(sessionMapper.findCompletedByUserId("user-1")).thenReturn(List.of(sessionTopic1, sessionTopic2));

		List<com.remelearning.english.grammar.library.dto.GrammarLibraryHistoryEntryDto> history =
				service.getHistoryForUser("user-1");

		assertThat(history).extracting("topicId").containsExactly(1L, 2L);
	}

	private void simulateGeneratedSessionId(Long id) {
		AtomicLong holder = new AtomicLong(id);
		org.mockito.Mockito.doAnswer(invocation -> {
			GrammarLibrarySession s = invocation.getArgument(0);
			s.setId(holder.get());
			return null;
		}).when(sessionMapper).insertSession(any(GrammarLibrarySession.class));
	}

	private String toJson(Object value) {
		try {
			return objectMapper.writeValueAsString(value);
		} catch (Exception ex) {
			throw new RuntimeException(ex);
		}
	}
}
