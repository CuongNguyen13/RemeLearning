package com.remelearning.english.listening.library.service;

import com.remelearning.english.listening.library.domain.ListeningLibraryAttempt;
import com.remelearning.english.listening.library.domain.ListeningLibraryQuestion;
import com.remelearning.english.listening.library.domain.ListeningLibrarySection;
import com.remelearning.english.listening.library.domain.ListeningLibraryTopic;
import com.remelearning.english.listening.library.domain.ListeningTopicProgress;
import com.remelearning.english.listening.library.domain.ListeningTopicStatus;
import com.remelearning.english.listening.library.dto.SubmitListeningAnswersRequest;
import com.remelearning.english.listening.library.generator.LlmListeningLibraryGenerator;
import com.remelearning.english.listening.library.domain.ListeningLibraryAttemptAnswer;
import com.remelearning.english.listening.library.mapper.ListeningLibraryAttemptAnswerMapper;
import com.remelearning.english.listening.library.mapper.ListeningLibraryAttemptMapper;
import com.remelearning.english.listening.library.mapper.ListeningLibraryQuestionMapper;
import com.remelearning.english.listening.library.mapper.ListeningLibrarySectionMapper;
import com.remelearning.english.listening.library.mapper.ListeningLibraryTopicMapper;
import com.remelearning.english.listening.library.mapper.ListeningTopicProgressMapper;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.any;
import org.mockito.ArgumentCaptor;

class ListeningLibraryServiceImplTest {

	@Test
	void getTopicsBootstrapsFirstTopicAndDefaultsMissingRowsToLocked() {
		ListeningLibraryTopicMapper topicMapper = mock(ListeningLibraryTopicMapper.class);
		ListeningLibrarySectionMapper sectionMapper = mock(ListeningLibrarySectionMapper.class);
		ListeningLibraryQuestionMapper questionMapper = mock(ListeningLibraryQuestionMapper.class);
		ListeningTopicProgressMapper progressMapper = mock(ListeningTopicProgressMapper.class);
		ListeningLibraryAttemptMapper attemptMapper = mock(ListeningLibraryAttemptMapper.class);
		ListeningLibraryAttemptAnswerMapper attemptAnswerMapper = mock(ListeningLibraryAttemptAnswerMapper.class);
		LlmListeningLibraryGenerator generator = mock(LlmListeningLibraryGenerator.class);
		com.remelearning.english.listening.service.ListeningLearnService listeningLearnService =
				mock(com.remelearning.english.listening.service.ListeningLearnService.class);

		ListeningLibraryTopic topic1 = new ListeningLibraryTopic();
		topic1.setId(1L); topic1.setName("Travel"); topic1.setLevel("A1"); topic1.setSequenceOrder(1);
		ListeningLibraryTopic topic2 = new ListeningLibraryTopic();
		topic2.setId(2L); topic2.setName("Food"); topic2.setLevel("A1"); topic2.setSequenceOrder(2);

		when(topicMapper.findAll()).thenReturn(List.of(topic1, topic2));
		when(topicMapper.findBySequenceOrder(1)).thenReturn(topic1);
		when(progressMapper.findByUserId("user-1")).thenReturn(List.of());

		ListeningLibraryServiceImpl service = new ListeningLibraryServiceImpl(
				topicMapper, sectionMapper, questionMapper, progressMapper, attemptMapper, attemptAnswerMapper, generator, null,
				listeningLearnService);

		var result = service.getTopics("user-1");

		verify(progressMapper).bootstrapFirstTopic("user-1", 1L);
		assertThat(result).hasSize(2);
		assertThat(result.get(1).getStatus()).isEqualTo("LOCKED");
	}

	@Test
	void startOrResumeSectionThrowsWhenTopicIsLocked() {
		ListeningLibraryTopicMapper topicMapper = mock(ListeningLibraryTopicMapper.class);
		ListeningLibrarySectionMapper sectionMapper = mock(ListeningLibrarySectionMapper.class);
		ListeningLibraryQuestionMapper questionMapper = mock(ListeningLibraryQuestionMapper.class);
		ListeningTopicProgressMapper progressMapper = mock(ListeningTopicProgressMapper.class);
		ListeningLibraryAttemptMapper attemptMapper = mock(ListeningLibraryAttemptMapper.class);
		ListeningLibraryAttemptAnswerMapper attemptAnswerMapper = mock(ListeningLibraryAttemptAnswerMapper.class);
		LlmListeningLibraryGenerator generator = mock(LlmListeningLibraryGenerator.class);
		com.remelearning.english.listening.service.ListeningLearnService listeningLearnService =
				mock(com.remelearning.english.listening.service.ListeningLearnService.class);

		ListeningLibraryTopic topic = new ListeningLibraryTopic();
		topic.setId(1L); topic.setSequenceOrder(1);
		when(topicMapper.findById(1L)).thenReturn(topic);

		ListeningTopicProgress progress = ListeningTopicProgress.builder()
				.userId("user-1").topicId(1L).status(ListeningTopicStatus.LOCKED).build();
		when(progressMapper.findByUserIdAndTopicId("user-1", 1L)).thenReturn(progress);

		ListeningLibraryServiceImpl service = new ListeningLibraryServiceImpl(
				topicMapper, sectionMapper, questionMapper, progressMapper, attemptMapper, attemptAnswerMapper, generator, null,
				listeningLearnService);

		org.junit.jupiter.api.Assertions.assertThrows(
				com.remelearning.common.exception.BusinessException.class,
				() -> service.startOrResumeSection("user-1", 1L));
	}

	@Test
	void startOrResumeSectionThrowsWhenTopicDoesNotExist() {
		ListeningLibraryTopicMapper topicMapper = mock(ListeningLibraryTopicMapper.class);
		ListeningLibrarySectionMapper sectionMapper = mock(ListeningLibrarySectionMapper.class);
		ListeningLibraryQuestionMapper questionMapper = mock(ListeningLibraryQuestionMapper.class);
		ListeningTopicProgressMapper progressMapper = mock(ListeningTopicProgressMapper.class);
		ListeningLibraryAttemptMapper attemptMapper = mock(ListeningLibraryAttemptMapper.class);
		ListeningLibraryAttemptAnswerMapper attemptAnswerMapper = mock(ListeningLibraryAttemptAnswerMapper.class);
		LlmListeningLibraryGenerator generator = mock(LlmListeningLibraryGenerator.class);
		com.remelearning.english.listening.service.ListeningLearnService listeningLearnService =
				mock(com.remelearning.english.listening.service.ListeningLearnService.class);

		when(topicMapper.findById(999L)).thenReturn(null);

		ListeningLibraryServiceImpl service = new ListeningLibraryServiceImpl(
				topicMapper, sectionMapper, questionMapper, progressMapper, attemptMapper, attemptAnswerMapper, generator, null,
				listeningLearnService);

		org.junit.jupiter.api.Assertions.assertThrows(
				com.remelearning.common.exception.BusinessException.class,
				() -> service.startOrResumeSection("user-1", 999L));

		verify(progressMapper, org.mockito.Mockito.never()).findByUserIdAndTopicId(any(), any());
	}

	@Test
	void submitAnswersThrowsWhenSectionDoesNotExist() {
		ListeningLibraryTopicMapper topicMapper = mock(ListeningLibraryTopicMapper.class);
		ListeningLibrarySectionMapper sectionMapper = mock(ListeningLibrarySectionMapper.class);
		ListeningLibraryQuestionMapper questionMapper = mock(ListeningLibraryQuestionMapper.class);
		ListeningTopicProgressMapper progressMapper = mock(ListeningTopicProgressMapper.class);
		ListeningLibraryAttemptMapper attemptMapper = mock(ListeningLibraryAttemptMapper.class);
		ListeningLibraryAttemptAnswerMapper attemptAnswerMapper = mock(ListeningLibraryAttemptAnswerMapper.class);
		LlmListeningLibraryGenerator generator = mock(LlmListeningLibraryGenerator.class);
		com.remelearning.english.listening.service.ListeningLearnService listeningLearnService =
				mock(com.remelearning.english.listening.service.ListeningLearnService.class);

		when(sectionMapper.findById(404L)).thenReturn(null);

		ListeningLibraryServiceImpl service = new ListeningLibraryServiceImpl(
				topicMapper, sectionMapper, questionMapper, progressMapper, attemptMapper, attemptAnswerMapper, generator, null,
				listeningLearnService);

		SubmitListeningAnswersRequest req = new SubmitListeningAnswersRequest();
		req.setAnswers(List.of());

		org.junit.jupiter.api.Assertions.assertThrows(
				com.remelearning.common.exception.BusinessException.class,
				() -> service.submitAnswers("user-1", 404L, req));

		verify(questionMapper, org.mockito.Mockito.never()).findBySectionId(any());
	}

	@Test
	void submitAnswersThrowsCleanBadRequestWhenAnswersIsNull() {
		ListeningLibraryTopicMapper topicMapper = mock(ListeningLibraryTopicMapper.class);
		ListeningLibrarySectionMapper sectionMapper = mock(ListeningLibrarySectionMapper.class);
		ListeningLibraryQuestionMapper questionMapper = mock(ListeningLibraryQuestionMapper.class);
		ListeningTopicProgressMapper progressMapper = mock(ListeningTopicProgressMapper.class);
		ListeningLibraryAttemptMapper attemptMapper = mock(ListeningLibraryAttemptMapper.class);
		ListeningLibraryAttemptAnswerMapper attemptAnswerMapper = mock(ListeningLibraryAttemptAnswerMapper.class);
		LlmListeningLibraryGenerator generator = mock(LlmListeningLibraryGenerator.class);
		com.remelearning.english.listening.service.ListeningLearnService listeningLearnService =
				mock(com.remelearning.english.listening.service.ListeningLearnService.class);

		ListeningLibrarySection section = new ListeningLibrarySection();
		section.setId(100L); section.setTopicId(1L);
		when(sectionMapper.findById(100L)).thenReturn(section);

		ListeningLibraryServiceImpl service = new ListeningLibraryServiceImpl(
				topicMapper, sectionMapper, questionMapper, progressMapper, attemptMapper, attemptAnswerMapper, generator, null,
				listeningLearnService);

		SubmitListeningAnswersRequest req = new SubmitListeningAnswersRequest();
		// answers left null on purpose - simulates a request body that omits the field

		org.junit.jupiter.api.Assertions.assertThrows(
				com.remelearning.common.exception.BusinessException.class,
				() -> service.submitAnswers("user-1", 100L, req));

		verify(questionMapper, org.mockito.Mockito.never()).findBySectionId(any());
		verify(attemptMapper, org.mockito.Mockito.never()).insert(any());
	}

	@Test
	void submitAnswersComputesScoreMarksPassedAndUnlocksNextTopicAboveThreshold() {
		ListeningLibraryTopicMapper topicMapper = mock(ListeningLibraryTopicMapper.class);
		ListeningLibrarySectionMapper sectionMapper = mock(ListeningLibrarySectionMapper.class);
		ListeningLibraryQuestionMapper questionMapper = mock(ListeningLibraryQuestionMapper.class);
		ListeningTopicProgressMapper progressMapper = mock(ListeningTopicProgressMapper.class);
		ListeningLibraryAttemptMapper attemptMapper = mock(ListeningLibraryAttemptMapper.class);
		ListeningLibraryAttemptAnswerMapper attemptAnswerMapper = mock(ListeningLibraryAttemptAnswerMapper.class);
		LlmListeningLibraryGenerator generator = mock(LlmListeningLibraryGenerator.class);
		com.remelearning.english.listening.service.ListeningLearnService listeningLearnService =
				mock(com.remelearning.english.listening.service.ListeningLearnService.class);

		ListeningLibraryTopic topic = new ListeningLibraryTopic();
		topic.setId(1L); topic.setSequenceOrder(1);
		ListeningLibrarySection section = new ListeningLibrarySection();
		section.setId(100L); section.setTopicId(1L);
		when(sectionMapper.findById(100L)).thenReturn(section);
		when(topicMapper.findById(1L)).thenReturn(topic);

		ListeningLibraryTopic nextTopic = new ListeningLibraryTopic();
		nextTopic.setId(2L); nextTopic.setSequenceOrder(2);
		when(topicMapper.findBySequenceOrder(2)).thenReturn(nextTopic);
		when(progressMapper.findByUserIdAndTopicId("user-1", 2L)).thenReturn(
				ListeningTopicProgress.builder().userId("user-1").topicId(2L)
						.status(ListeningTopicStatus.UNLOCKED).build());

		ListeningLibraryQuestion q1 = new ListeningLibraryQuestion();
		q1.setId(1L); q1.setSectionId(100L); q1.setCorrectOption("A");
		ListeningLibraryQuestion q2 = new ListeningLibraryQuestion();
		q2.setId(2L); q2.setSectionId(100L); q2.setCorrectOption("B");
		when(questionMapper.findBySectionId(100L)).thenReturn(List.of(q1, q2));

		SubmitListeningAnswersRequest req = new SubmitListeningAnswersRequest();
		req.setAnswers(List.of(
				new SubmitListeningAnswersRequest.AnswerItem(1L, "A"),
				new SubmitListeningAnswersRequest.AnswerItem(2L, "B")));

		ListeningLibraryServiceImpl service = new ListeningLibraryServiceImpl(
				topicMapper, sectionMapper, questionMapper, progressMapper, attemptMapper, attemptAnswerMapper, generator, null,
				listeningLearnService);

		var response = service.submitAnswers("user-1", 100L, req);

		assertThat(response.getCorrectCount()).isEqualTo(2);
		assertThat(response.getScore()).isEqualTo(1.0);
		assertThat(response.isTopicPassed()).isTrue();
		assertThat(response.getNextTopicId()).isEqualTo(2L);
		verify(attemptMapper).insert(any());
		verify(progressMapper).markPassed("user-1", 1L);
		verify(progressMapper).unlockIfLocked("user-1", 2L);
	}

	@Test
	void submitAnswersPersistsOnePerQuestionAnswerRowWithCorrectIsCorrectFlagAndAttemptId() {
		ListeningLibraryTopicMapper topicMapper = mock(ListeningLibraryTopicMapper.class);
		ListeningLibrarySectionMapper sectionMapper = mock(ListeningLibrarySectionMapper.class);
		ListeningLibraryQuestionMapper questionMapper = mock(ListeningLibraryQuestionMapper.class);
		ListeningTopicProgressMapper progressMapper = mock(ListeningTopicProgressMapper.class);
		ListeningLibraryAttemptMapper attemptMapper = mock(ListeningLibraryAttemptMapper.class);
		ListeningLibraryAttemptAnswerMapper attemptAnswerMapper = mock(ListeningLibraryAttemptAnswerMapper.class);
		LlmListeningLibraryGenerator generator = mock(LlmListeningLibraryGenerator.class);
		com.remelearning.english.listening.service.ListeningLearnService listeningLearnService =
				mock(com.remelearning.english.listening.service.ListeningLearnService.class);

		ListeningLibraryTopic topic = new ListeningLibraryTopic();
		topic.setId(1L); topic.setSequenceOrder(1);
		ListeningLibrarySection section = new ListeningLibrarySection();
		section.setId(100L); section.setTopicId(1L);
		when(sectionMapper.findById(100L)).thenReturn(section);
		when(topicMapper.findById(1L)).thenReturn(topic);
		// Below-threshold score on purpose, so this test doesn't need to stub the pass/unlock path.
		when(topicMapper.findBySequenceOrder(2)).thenReturn(null);

		ListeningLibraryQuestion q1 = new ListeningLibraryQuestion();
		q1.setId(1L); q1.setSectionId(100L); q1.setCorrectOption("A");
		ListeningLibraryQuestion q2 = new ListeningLibraryQuestion();
		q2.setId(2L); q2.setSectionId(100L); q2.setCorrectOption("B");
		when(questionMapper.findBySectionId(100L)).thenReturn(List.of(q1, q2));

		// MyBatis' useGeneratedKeys populates attempt.id as a side effect of insert() - simulate
		// that here so the per-answer loop (which runs after the insert) sees a real attemptId.
		doAnswer(invocation -> {
			com.remelearning.english.listening.library.domain.ListeningLibraryAttempt attempt = invocation.getArgument(0);
			attempt.setId(500L);
			return null;
		}).when(attemptMapper).insert(any());

		SubmitListeningAnswersRequest req = new SubmitListeningAnswersRequest();
		req.setAnswers(List.of(
				new SubmitListeningAnswersRequest.AnswerItem(1L, "A"),
				new SubmitListeningAnswersRequest.AnswerItem(2L, "WRONG")));

		ListeningLibraryServiceImpl service = new ListeningLibraryServiceImpl(
				topicMapper, sectionMapper, questionMapper, progressMapper, attemptMapper, attemptAnswerMapper, generator, null,
				listeningLearnService);

		service.submitAnswers("user-1", 100L, req);

		ArgumentCaptor<ListeningLibraryAttemptAnswer> captor = ArgumentCaptor.forClass(ListeningLibraryAttemptAnswer.class);
		verify(attemptAnswerMapper, org.mockito.Mockito.times(2)).insert(captor.capture());
		List<ListeningLibraryAttemptAnswer> saved = captor.getAllValues();

		assertThat(saved).hasSize(2);
		ListeningLibraryAttemptAnswer correctAnswer = saved.stream().filter(a -> a.getQuestionId().equals(1L)).findFirst().orElseThrow();
		assertThat(correctAnswer.getAttemptId()).isEqualTo(500L);
		assertThat(correctAnswer.getSelectedOption()).isEqualTo("A");
		assertThat(correctAnswer.getCorrectOption()).isEqualTo("A");
		assertThat(correctAnswer.getIsCorrect()).isTrue();

		ListeningLibraryAttemptAnswer wrongAnswer = saved.stream().filter(a -> a.getQuestionId().equals(2L)).findFirst().orElseThrow();
		assertThat(wrongAnswer.getAttemptId()).isEqualTo(500L);
		assertThat(wrongAnswer.getSelectedOption()).isEqualTo("WRONG");
		assertThat(wrongAnswer.getCorrectOption()).isEqualTo("B");
		assertThat(wrongAnswer.getIsCorrect()).isFalse();
	}

	@Test
	void generatePracticeFromSectionThrowsWhenSectionDoesNotExist() {
		ListeningLibraryTopicMapper topicMapper = mock(ListeningLibraryTopicMapper.class);
		ListeningLibrarySectionMapper sectionMapper = mock(ListeningLibrarySectionMapper.class);
		ListeningLibraryQuestionMapper questionMapper = mock(ListeningLibraryQuestionMapper.class);
		ListeningTopicProgressMapper progressMapper = mock(ListeningTopicProgressMapper.class);
		ListeningLibraryAttemptMapper attemptMapper = mock(ListeningLibraryAttemptMapper.class);
		ListeningLibraryAttemptAnswerMapper attemptAnswerMapper = mock(ListeningLibraryAttemptAnswerMapper.class);
		LlmListeningLibraryGenerator generator = mock(LlmListeningLibraryGenerator.class);
		com.remelearning.english.listening.service.ListeningLearnService listeningLearnService =
				mock(com.remelearning.english.listening.service.ListeningLearnService.class);

		when(sectionMapper.findById(404L)).thenReturn(null);

		ListeningLibraryServiceImpl service = new ListeningLibraryServiceImpl(
				topicMapper, sectionMapper, questionMapper, progressMapper, attemptMapper, attemptAnswerMapper, generator, null,
				listeningLearnService);

		org.junit.jupiter.api.Assertions.assertThrows(
				com.remelearning.common.exception.BusinessException.class,
				() -> service.generatePracticeFromSection("user-1", 404L));
	}

	@Test
	void generatePracticeFromSectionReturnsEmptyWhenLearnerHasNoCompletedAttempt() {
		ListeningLibraryTopicMapper topicMapper = mock(ListeningLibraryTopicMapper.class);
		ListeningLibrarySectionMapper sectionMapper = mock(ListeningLibrarySectionMapper.class);
		ListeningLibraryQuestionMapper questionMapper = mock(ListeningLibraryQuestionMapper.class);
		ListeningTopicProgressMapper progressMapper = mock(ListeningTopicProgressMapper.class);
		ListeningLibraryAttemptMapper attemptMapper = mock(ListeningLibraryAttemptMapper.class);
		ListeningLibraryAttemptAnswerMapper attemptAnswerMapper = mock(ListeningLibraryAttemptAnswerMapper.class);
		LlmListeningLibraryGenerator generator = mock(LlmListeningLibraryGenerator.class);
		com.remelearning.english.listening.service.ListeningLearnService listeningLearnService =
				mock(com.remelearning.english.listening.service.ListeningLearnService.class);

		ListeningLibrarySection section = new ListeningLibrarySection();
		section.setId(100L); section.setTopicId(1L);
		when(sectionMapper.findById(100L)).thenReturn(section);
		when(attemptMapper.findByUserId("user-1")).thenReturn(List.of());

		ListeningLibraryServiceImpl service = new ListeningLibraryServiceImpl(
				topicMapper, sectionMapper, questionMapper, progressMapper, attemptMapper, attemptAnswerMapper, generator, null,
				listeningLearnService);

		var result = service.generatePracticeFromSection("user-1", 100L);

		assertThat(result).isEmpty();
		verify(listeningLearnService, org.mockito.Mockito.never()).generatePracticeForKeywords(any(), any(), any(), any());
	}

	@Test
	void generatePracticeFromSectionReturnsEmptyWhenLatestAttemptHadNoMistakes() {
		ListeningLibraryTopicMapper topicMapper = mock(ListeningLibraryTopicMapper.class);
		ListeningLibrarySectionMapper sectionMapper = mock(ListeningLibrarySectionMapper.class);
		ListeningLibraryQuestionMapper questionMapper = mock(ListeningLibraryQuestionMapper.class);
		ListeningTopicProgressMapper progressMapper = mock(ListeningTopicProgressMapper.class);
		ListeningLibraryAttemptMapper attemptMapper = mock(ListeningLibraryAttemptMapper.class);
		ListeningLibraryAttemptAnswerMapper attemptAnswerMapper = mock(ListeningLibraryAttemptAnswerMapper.class);
		LlmListeningLibraryGenerator generator = mock(LlmListeningLibraryGenerator.class);
		com.remelearning.english.listening.service.ListeningLearnService listeningLearnService =
				mock(com.remelearning.english.listening.service.ListeningLearnService.class);

		ListeningLibrarySection section = new ListeningLibrarySection();
		section.setId(100L); section.setTopicId(1L);
		when(sectionMapper.findById(100L)).thenReturn(section);

		ListeningLibraryAttempt attempt = ListeningLibraryAttempt.builder()
				.id(500L).userId("user-1").sectionId(100L)
				.completedAt(java.time.Instant.now()).build();
		when(attemptMapper.findByUserId("user-1")).thenReturn(List.of(attempt));

		ListeningLibraryAttemptAnswer correctAnswer = new ListeningLibraryAttemptAnswer();
		correctAnswer.setQuestionId(1L); correctAnswer.setIsCorrect(true);
		when(attemptAnswerMapper.findByAttemptId(500L)).thenReturn(List.of(correctAnswer));

		ListeningLibraryServiceImpl service = new ListeningLibraryServiceImpl(
				topicMapper, sectionMapper, questionMapper, progressMapper, attemptMapper, attemptAnswerMapper, generator, null,
				listeningLearnService);

		var result = service.generatePracticeFromSection("user-1", 100L);

		assertThat(result).isEmpty();
		verify(listeningLearnService, org.mockito.Mockito.never()).generatePracticeForKeywords(any(), any(), any(), any());
	}

	@Test
	void generatePracticeFromSectionUsesTopicNameAsTargetKeywordWhenLatestAttemptHadMistakes() {
		ListeningLibraryTopicMapper topicMapper = mock(ListeningLibraryTopicMapper.class);
		ListeningLibrarySectionMapper sectionMapper = mock(ListeningLibrarySectionMapper.class);
		ListeningLibraryQuestionMapper questionMapper = mock(ListeningLibraryQuestionMapper.class);
		ListeningTopicProgressMapper progressMapper = mock(ListeningTopicProgressMapper.class);
		ListeningLibraryAttemptMapper attemptMapper = mock(ListeningLibraryAttemptMapper.class);
		ListeningLibraryAttemptAnswerMapper attemptAnswerMapper = mock(ListeningLibraryAttemptAnswerMapper.class);
		LlmListeningLibraryGenerator generator = mock(LlmListeningLibraryGenerator.class);
		com.remelearning.english.listening.service.ListeningLearnService listeningLearnService =
				mock(com.remelearning.english.listening.service.ListeningLearnService.class);

		ListeningLibrarySection section = new ListeningLibrarySection();
		section.setId(100L); section.setTopicId(1L);
		when(sectionMapper.findById(100L)).thenReturn(section);

		ListeningLibraryTopic topic = new ListeningLibraryTopic();
		topic.setId(1L); topic.setName("Airport announcements"); topic.setLevel("B1");
		when(topicMapper.findById(1L)).thenReturn(topic);

		// Two attempts on the same section - the older (earlier completedAt) one is all-correct, the
		// newer one has a miss; only the newer (most recent) attempt's answers should be consulted.
		ListeningLibraryAttempt olderAttempt = ListeningLibraryAttempt.builder()
				.id(400L).userId("user-1").sectionId(100L)
				.completedAt(java.time.Instant.now().minusSeconds(3600)).build();
		ListeningLibraryAttempt newerAttempt = ListeningLibraryAttempt.builder()
				.id(500L).userId("user-1").sectionId(100L)
				.completedAt(java.time.Instant.now()).build();
		when(attemptMapper.findByUserId("user-1")).thenReturn(List.of(olderAttempt, newerAttempt));

		ListeningLibraryAttemptAnswer wrongAnswer = new ListeningLibraryAttemptAnswer();
		wrongAnswer.setQuestionId(1L); wrongAnswer.setIsCorrect(false);
		when(attemptAnswerMapper.findByAttemptId(500L)).thenReturn(List.of(wrongAnswer));

		List<com.remelearning.english.listening.dto.ListeningPracticeItemDto> expected = List.of();
		when(listeningLearnService.generatePracticeForKeywords(
				"user-1", List.of("Airport announcements"), "B1", null)).thenReturn(expected);

		ListeningLibraryServiceImpl service = new ListeningLibraryServiceImpl(
				topicMapper, sectionMapper, questionMapper, progressMapper, attemptMapper, attemptAnswerMapper, generator, null,
				listeningLearnService);

		var result = service.generatePracticeFromSection("user-1", 100L);

		assertThat(result).isSameAs(expected);
		verify(attemptAnswerMapper, org.mockito.Mockito.never()).findByAttemptId(400L);
		verify(listeningLearnService).generatePracticeForKeywords("user-1", List.of("Airport announcements"), "B1", null);
	}
}
