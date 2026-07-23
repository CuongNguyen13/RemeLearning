package com.remelearning.english.listening.library.service;

import com.remelearning.english.listening.library.domain.ListeningLibraryQuestion;
import com.remelearning.english.listening.library.domain.ListeningLibrarySection;
import com.remelearning.english.listening.library.domain.ListeningLibraryTopic;
import com.remelearning.english.listening.library.domain.ListeningTopicProgress;
import com.remelearning.english.listening.library.domain.ListeningTopicStatus;
import com.remelearning.english.listening.library.dto.SubmitListeningAnswersRequest;
import com.remelearning.english.listening.library.generator.LlmListeningLibraryGenerator;
import com.remelearning.english.listening.library.mapper.ListeningLibraryAttemptMapper;
import com.remelearning.english.listening.library.mapper.ListeningLibraryQuestionMapper;
import com.remelearning.english.listening.library.mapper.ListeningLibrarySectionMapper;
import com.remelearning.english.listening.library.mapper.ListeningLibraryTopicMapper;
import com.remelearning.english.listening.library.mapper.ListeningTopicProgressMapper;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.any;

class ListeningLibraryServiceImplTest {

	@Test
	void getTopicsBootstrapsFirstTopicAndDefaultsMissingRowsToLocked() {
		ListeningLibraryTopicMapper topicMapper = mock(ListeningLibraryTopicMapper.class);
		ListeningLibrarySectionMapper sectionMapper = mock(ListeningLibrarySectionMapper.class);
		ListeningLibraryQuestionMapper questionMapper = mock(ListeningLibraryQuestionMapper.class);
		ListeningTopicProgressMapper progressMapper = mock(ListeningTopicProgressMapper.class);
		ListeningLibraryAttemptMapper attemptMapper = mock(ListeningLibraryAttemptMapper.class);
		LlmListeningLibraryGenerator generator = mock(LlmListeningLibraryGenerator.class);

		ListeningLibraryTopic topic1 = new ListeningLibraryTopic();
		topic1.setId(1L); topic1.setName("Travel"); topic1.setLevel("A1"); topic1.setSequenceOrder(1);
		ListeningLibraryTopic topic2 = new ListeningLibraryTopic();
		topic2.setId(2L); topic2.setName("Food"); topic2.setLevel("A1"); topic2.setSequenceOrder(2);

		when(topicMapper.findAll()).thenReturn(List.of(topic1, topic2));
		when(topicMapper.findBySequenceOrder(1)).thenReturn(topic1);
		when(progressMapper.findByUserId("user-1")).thenReturn(List.of());

		ListeningLibraryServiceImpl service = new ListeningLibraryServiceImpl(
				topicMapper, sectionMapper, questionMapper, progressMapper, attemptMapper, generator, null);

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
		LlmListeningLibraryGenerator generator = mock(LlmListeningLibraryGenerator.class);

		ListeningTopicProgress progress = ListeningTopicProgress.builder()
				.userId("user-1").topicId(1L).status(ListeningTopicStatus.LOCKED).build();
		when(progressMapper.findByUserIdAndTopicId("user-1", 1L)).thenReturn(progress);

		ListeningLibraryServiceImpl service = new ListeningLibraryServiceImpl(
				topicMapper, sectionMapper, questionMapper, progressMapper, attemptMapper, generator, null);

		org.junit.jupiter.api.Assertions.assertThrows(
				com.remelearning.common.exception.BusinessException.class,
				() -> service.startOrResumeSection("user-1", 1L));
	}

	@Test
	void submitAnswersComputesScoreMarksPassedAndUnlocksNextTopicAboveThreshold() {
		ListeningLibraryTopicMapper topicMapper = mock(ListeningLibraryTopicMapper.class);
		ListeningLibrarySectionMapper sectionMapper = mock(ListeningLibrarySectionMapper.class);
		ListeningLibraryQuestionMapper questionMapper = mock(ListeningLibraryQuestionMapper.class);
		ListeningTopicProgressMapper progressMapper = mock(ListeningTopicProgressMapper.class);
		ListeningLibraryAttemptMapper attemptMapper = mock(ListeningLibraryAttemptMapper.class);
		LlmListeningLibraryGenerator generator = mock(LlmListeningLibraryGenerator.class);

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
				topicMapper, sectionMapper, questionMapper, progressMapper, attemptMapper, generator, null);

		var response = service.submitAnswers("user-1", 100L, req);

		assertThat(response.getCorrectCount()).isEqualTo(2);
		assertThat(response.getScore()).isEqualTo(1.0);
		assertThat(response.isTopicPassed()).isTrue();
		assertThat(response.getNextTopicId()).isEqualTo(2L);
		verify(attemptMapper).insert(any());
		verify(progressMapper).markPassed("user-1", 1L);
		verify(progressMapper).unlockIfLocked("user-1", 2L);
	}
}
