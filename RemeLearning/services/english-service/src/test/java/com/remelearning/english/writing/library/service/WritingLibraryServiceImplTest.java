package com.remelearning.english.writing.library.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.remelearning.common.exception.BusinessException;
import com.remelearning.english.writing.domain.WritingCriteriaScores;
import com.remelearning.english.writing.domain.WritingErrorItem;
import com.remelearning.english.writing.domain.WritingTaskType;
import com.remelearning.english.writing.grading.WritingErrorPipeline;
import com.remelearning.english.writing.grading.WritingGrade;
import com.remelearning.english.writing.grading.WritingGrader;
import com.remelearning.english.writing.library.domain.WritingLibraryAttempt;
import com.remelearning.english.writing.library.domain.WritingLibraryPrompt;
import com.remelearning.english.writing.library.domain.WritingLibraryTopic;
import com.remelearning.english.writing.library.domain.WritingTopicProgress;
import com.remelearning.english.writing.library.domain.WritingTopicStatus;
import com.remelearning.english.writing.library.dto.SubmitWritingLibraryAnswerRequest;
import com.remelearning.english.writing.library.dto.SubmitWritingLibraryAnswerResponse;
import com.remelearning.english.writing.library.dto.WritingLibraryPromptDto;
import com.remelearning.english.writing.library.dto.WritingLibraryTopicDto;
import com.remelearning.english.writing.library.generator.WritingLibraryContentGenerator;
import com.remelearning.english.writing.library.mapper.WritingLibraryAttemptMapper;
import com.remelearning.english.writing.library.mapper.WritingLibraryPromptMapper;
import com.remelearning.english.writing.library.mapper.WritingLibraryTopicMapper;
import com.remelearning.english.writing.library.mapper.WritingTopicProgressMapper;
import com.remelearning.english.writing.service.WritingLearnService;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class WritingLibraryServiceImplTest {

	private final WritingLibraryTopicMapper topicMapper = mock(WritingLibraryTopicMapper.class);
	private final WritingLibraryPromptMapper promptMapper = mock(WritingLibraryPromptMapper.class);
	private final WritingTopicProgressMapper progressMapper = mock(WritingTopicProgressMapper.class);
	private final WritingLibraryAttemptMapper attemptMapper = mock(WritingLibraryAttemptMapper.class);
	private final WritingLibraryContentGenerator generator = mock(WritingLibraryContentGenerator.class);
	private final WritingGrader grader = mock(WritingGrader.class);
	private final WritingErrorPipeline errorPipeline = mock(WritingErrorPipeline.class);
	private final WritingLearnService writingLearnService = mock(WritingLearnService.class);
	private final ObjectMapper objectMapper = new ObjectMapper();
	private final WritingLibraryServiceImpl service = new WritingLibraryServiceImpl(
			topicMapper, promptMapper, progressMapper, attemptMapper, generator, grader, errorPipeline,
			writingLearnService, objectMapper);

	// --- taxonomy scoping ---

	@Test
	void getTopicsBootstrapsAndReportsOnlyTheRequestedAxis() {
		when(topicMapper.findByTaxonomyAndSequenceOrder("genre", 1)).thenReturn(topic(10L, "genre", 1, "Email"));
		when(progressMapper.findByUserId("user-1")).thenReturn(List.of());
		when(attemptMapper.findByUserId("user-1")).thenReturn(List.of());
		when(topicMapper.findByTaxonomy("genre")).thenReturn(List.of(topic(10L, "genre", 1, "Email")));
		when(promptMapper.findByTopicId(10L)).thenReturn(List.of());

		List<WritingLibraryTopicDto> topics = service.getTopics("user-1", "genre");

		// The first topic of THIS axis is opened, not a globally-first topic.
		verify(progressMapper).bootstrapFirstTopic("user-1", 10L);
		verify(topicMapper).findByTaxonomy("genre");
		assertThat(topics).singleElement().satisfies(dto -> {
			assertThat(dto.getTaxonomy()).isEqualTo("genre");
			assertThat(dto.getStatus()).isEqualTo("LOCKED");
			assertThat(dto.getPassedPromptCount()).isZero();
			assertThat(dto.getTargetPromptCount()).isGreaterThanOrEqualTo(3);
		});
	}

	@Test
	void getTopicsRejectsAnUnknownAxisInsteadOfReturningAnEmptyCatalogue() {
		assertThatThrownBy(() -> service.getTopics("user-1", "phonics"))
				.isInstanceOf(BusinessException.class);
		verify(topicMapper, never()).findByTaxonomy(anyString());
	}

	@Test
	void getTopicsAcceptsTheAxisCodeCaseInsensitively() {
		when(topicMapper.findByTaxonomyAndSequenceOrder("vocab_theme", 1)).thenReturn(null);
		when(progressMapper.findByUserId("user-1")).thenReturn(List.of());
		when(attemptMapper.findByUserId("user-1")).thenReturn(List.of());
		when(topicMapper.findByTaxonomy("vocab_theme")).thenReturn(List.of());

		assertThat(service.getTopics("user-1", "VOCAB_THEME")).isEmpty();
		verify(topicMapper).findByTaxonomy("vocab_theme");
	}

	@Test
	void passingTheWholeChainUnlocksTheNextTopicOnTheSameAxisOnly() {
		WritingLibraryTopic grammarTopic = topic(3L, "grammar", 7, "Present Perfect");
		// targetPromptCount(3) = 3 + (3 % 4) = 6
		List<WritingLibraryPrompt> chain = chainOf(6, 3L);
		stubSubmit(100L, grammarTopic, chain);
		// Every prompt in the chain already passed (including the one just submitted).
		when(attemptMapper.findByUserId("user-1")).thenReturn(passedAttemptsFor(chain));
		when(topicMapper.findByTaxonomyAndSequenceOrder("grammar", 8))
				.thenReturn(topic(4L, "grammar", 8, "Past Perfect"));
		when(progressMapper.findByUserIdAndTopicId("user-1", 4L))
				.thenReturn(progress(4L, WritingTopicStatus.UNLOCKED));
		stubPassingGrade();

		SubmitWritingLibraryAnswerResponse response = service.submitAnswer("user-1", 100L, submitRequest());

		assertThat(response.isTopicPassed()).isTrue();
		assertThat(response.getNextTopicId()).isEqualTo(4L);
		assertThat(response.isNextTopicUnlocked()).isTrue();
		// Axis-scoped lookup: sequence_order 8 must be resolved within "grammar", never globally.
		verify(topicMapper).findByTaxonomyAndSequenceOrder("grammar", 8);
		verify(progressMapper).markPassed("user-1", 3L);
		verify(progressMapper).unlockIfLocked("user-1", 4L);
	}

	@Test
	void aTopicIsNotPassedWhileItsChainIsStillShorterThanItsTarget() {
		WritingLibraryTopic topic = topic(3L, "grammar", 7, "Present Perfect");
		List<WritingLibraryPrompt> chain = chainOf(2, 3L);
		stubSubmit(100L, topic, chain);
		when(attemptMapper.findByUserId("user-1")).thenReturn(passedAttemptsFor(chain));
		stubPassingGrade();

		SubmitWritingLibraryAnswerResponse response = service.submitAnswer("user-1", 100L, submitRequest());

		assertThat(response.isPassed()).isTrue();
		assertThat(response.isTopicPassed()).isFalse();
		verify(progressMapper, never()).markPassed(anyString(), any());
		verify(topicMapper, never()).findByTaxonomyAndSequenceOrder(anyString(), anyInt());
	}

	@Test
	void aFailingScoreNeverAdvancesProgress() {
		WritingLibraryTopic topic = topic(3L, "grammar", 7, "Present Perfect");
		List<WritingLibraryPrompt> chain = chainOf(6, 3L);
		stubSubmit(100L, topic, chain);
		when(attemptMapper.findByUserId("user-1")).thenReturn(passedAttemptsFor(chain));
		when(grader.grade(any(), any(), any(), any())).thenReturn(grade(List.of()));
		when(errorPipeline.averageCriteria(any())).thenReturn(0.4);

		SubmitWritingLibraryAnswerResponse response = service.submitAnswer("user-1", 100L, submitRequest());

		assertThat(response.isPassed()).isFalse();
		assertThat(response.isTopicPassed()).isFalse();
		verify(progressMapper, never()).markPassed(anyString(), any());
	}

	// --- grading reuse ---

	@Test
	void submitRoutesErrorsThroughTheSharedWeakPointPipeline() {
		WritingLibraryTopic topic = topic(3L, "grammar", 7, "Present Perfect");
		stubSubmit(100L, topic, chainOf(6, 3L));
		when(attemptMapper.findByUserId("user-1")).thenReturn(List.of());
		List<WritingErrorItem> errors = List.of(WritingErrorItem.builder()
				.label("past perfect").category("grammar").build());
		when(grader.grade(any(), any(), any(), any())).thenReturn(grade(errors));
		when(errorPipeline.averageCriteria(any())).thenReturn(0.8);
		when(errorPipeline.buildActionAdvice(errors)).thenReturn(List.of("Ôn lại quy tắc."));

		SubmitWritingLibraryAnswerResponse response = service.submitAnswer("user-1", 100L, submitRequest());

		// Both tabs must share one implementation of the weak-point routing, or they drift apart.
		verify(errorPipeline).feedWeakPoints("user-1", errors);
		assertThat(response.getErrors()).isEqualTo(errors);
		assertThat(response.getActionAdvice()).containsExactly("Ôn lại quy tắc.");
	}

	@Test
	void submitRevealsTheReferenceAnswerAndPersistsTheGradersOutput() {
		WritingLibraryTopic topic = topic(3L, "grammar", 7, "Present Perfect");
		stubSubmit(100L, topic, chainOf(6, 3L));
		when(attemptMapper.findByUserId("user-1")).thenReturn(List.of());
		when(grader.grade(any(), any(), any(), any())).thenReturn(grade(List.of()));
		when(errorPipeline.averageCriteria(any())).thenReturn(0.9);

		SubmitWritingLibraryAnswerResponse response = service.submitAnswer("user-1", 100L, submitRequest());

		assertThat(response.getReferenceAnswer()).isEqualTo("Reference answer.");
		org.mockito.ArgumentCaptor<WritingLibraryAttempt> captor =
				org.mockito.ArgumentCaptor.forClass(WritingLibraryAttempt.class);
		verify(attemptMapper).insert(captor.capture());
		assertThat(captor.getValue().getScore()).isEqualTo(0.9);
		assertThat(captor.getValue().getCriteriaJson()).contains("grammar");
		assertThat(captor.getValue().getStartedAt()).isNotNull();
		assertThat(captor.getValue().getCompletedAt()).isNotNull();
	}

	// --- prompt chain ---

	@Test
	void startOrResumeGeneratesAPromptWhileTheChainIsStillShort() {
		WritingLibraryTopic topic = topic(3L, "grammar", 7, "Present Perfect");
		when(topicMapper.findById(3L)).thenReturn(topic);
		when(progressMapper.findByUserIdAndTopicId("user-1", 3L))
				.thenReturn(progress(3L, WritingTopicStatus.UNLOCKED));
		when(promptMapper.findByTopicId(3L)).thenReturn(List.of());
		when(attemptMapper.findByUserId("user-1")).thenReturn(List.of());
		when(generator.generatePrompt(eq(topic), eq(WritingTaskType.COMPOSE), any()))
				.thenReturn(prompt(50L, 3L, WritingTaskType.COMPOSE));

		WritingLibraryPromptDto dto = service.startOrResumePrompt("user-1", 3L, WritingTaskType.COMPOSE, null);

		assertThat(dto.getPromptId()).isEqualTo(50L);
		assertThat(dto.getTopicName()).isEqualTo("Present Perfect");
		assertThat(dto.getSourceLang()).isEqualTo("vi");
		verify(progressMapper).markInProgress("user-1", 3L);
	}

	@Test
	void startOrResumeReturnsTheFirstUnpassedPromptWithoutGeneratingAnother() {
		WritingLibraryTopic topic = topic(3L, "grammar", 7, "Present Perfect");
		when(topicMapper.findById(3L)).thenReturn(topic);
		when(progressMapper.findByUserIdAndTopicId("user-1", 3L))
				.thenReturn(progress(3L, WritingTopicStatus.IN_PROGRESS));
		List<WritingLibraryPrompt> chain = chainOf(3, 3L);
		when(promptMapper.findByTopicId(3L)).thenReturn(chain);
		// Only the first prompt is passed, so the second is the one owed.
		when(attemptMapper.findByUserId("user-1")).thenReturn(passedAttemptsFor(chain.subList(0, 1)));

		WritingLibraryPromptDto dto = service.startOrResumePrompt("user-1", 3L, WritingTaskType.COMPOSE, null);

		assertThat(dto.getPromptId()).isEqualTo(chain.get(1).getId());
		assertThat(dto.getPosition()).isEqualTo(2);
		verify(generator, never()).generatePrompt(any(), any(), any());
	}

	@Test
	void startOrResumeRejectsALockedTopic() {
		when(topicMapper.findById(3L)).thenReturn(topic(3L, "grammar", 7, "Present Perfect"));
		when(progressMapper.findByUserIdAndTopicId("user-1", 3L)).thenReturn(null);

		assertThatThrownBy(() -> service.startOrResumePrompt("user-1", 3L, WritingTaskType.COMPOSE, null))
				.isInstanceOf(BusinessException.class);
	}

	// --- retry ---

	@Test
	void generatePracticeFromAttemptFeedsThatAttemptsLabelsIntoTheLearnTabsBank() {
		when(attemptMapper.findByIdAndUserId(70L, "user-1")).thenReturn(WritingLibraryAttempt.builder()
				.id(70L).promptId(100L)
				.errorsJson(toJson(List.of(
						WritingErrorItem.builder().label("past perfect").category("grammar").build())))
				.build());
		when(promptMapper.findById(100L)).thenReturn(prompt(100L, 3L, WritingTaskType.TRANSLATE_VI_EN));
		when(topicMapper.findById(3L)).thenReturn(topic(3L, "grammar", 7, "Present Perfect"));

		service.generatePracticeFromAttempt("user-1", 70L, null);

		verify(writingLearnService).generatePracticeForLabels(
				"user-1", WritingTaskType.TRANSLATE_VI_EN, List.of("past perfect"), "B1", null);
	}

	@Test
	void generatePracticeFromAttemptDoesNothingWhenThereWereNoMistakes() {
		when(attemptMapper.findByIdAndUserId(70L, "user-1")).thenReturn(WritingLibraryAttempt.builder()
				.id(70L).promptId(100L).errorsJson("[]").build());

		assertThat(service.generatePracticeFromAttempt("user-1", 70L, null)).isEmpty();
		verify(writingLearnService, never()).generatePracticeForLabels(any(), any(), any(), any(), any());
	}

	@Test
	void generatePracticeFromAttemptRejectsAnotherLearnersAttempt() {
		when(attemptMapper.findByIdAndUserId(99L, "user-1")).thenReturn(null);

		assertThatThrownBy(() -> service.generatePracticeFromAttempt("user-1", 99L, null))
				.isInstanceOf(BusinessException.class);
	}

	// --- helpers ---

	private void stubSubmit(Long promptId, WritingLibraryTopic topic, List<WritingLibraryPrompt> chain) {
		when(promptMapper.findById(promptId)).thenReturn(prompt(promptId, topic.getId(), WritingTaskType.COMPOSE));
		when(topicMapper.findById(topic.getId())).thenReturn(topic);
		when(promptMapper.findByTopicId(topic.getId())).thenReturn(chain);
	}

	private void stubPassingGrade() {
		when(grader.grade(any(), any(), any(), any())).thenReturn(grade(List.of()));
		when(errorPipeline.averageCriteria(any())).thenReturn(0.85);
	}

	private WritingGrade grade(List<WritingErrorItem> errors) {
		return new WritingGrade(
				WritingCriteriaScores.builder().grammar(0.8).vocabulary(0.8).coherence(0.8).taskResponse(0.8).build(),
				"Corrected.", errors, "Nhận xét.");
	}

	private SubmitWritingLibraryAnswerRequest submitRequest() {
		SubmitWritingLibraryAnswerRequest request = new SubmitWritingLibraryAnswerRequest();
		request.setSubmittedText("My text.");
		return request;
	}

	private WritingLibraryTopic topic(Long id, String taxonomy, int sequenceOrder, String name) {
		return WritingLibraryTopic.builder()
				.id(id).taxonomy(taxonomy).code(name.toLowerCase().replace(' ', '_')).name(name)
				.description("desc").level("B1").sequenceOrder(sequenceOrder)
				.build();
	}

	private WritingTopicProgress progress(Long topicId, WritingTopicStatus status) {
		return WritingTopicProgress.builder().userId("user-1").topicId(topicId).status(status).build();
	}

	private WritingLibraryPrompt prompt(Long id, Long topicId, WritingTaskType taskType) {
		return WritingLibraryPrompt.builder()
				.id(id).topicId(topicId).taskType(taskType)
				.promptText("Prompt text").referenceAnswer("Reference answer.").minWords(80)
				.build();
	}

	private List<WritingLibraryPrompt> chainOf(int size, Long topicId) {
		return java.util.stream.IntStream.rangeClosed(1, size)
				.mapToObj(i -> prompt(100L + i - 1, topicId, WritingTaskType.COMPOSE))
				.toList();
	}

	private List<WritingLibraryAttempt> passedAttemptsFor(List<WritingLibraryPrompt> prompts) {
		return prompts.stream()
				.map(prompt -> WritingLibraryAttempt.builder()
						.id(prompt.getId() + 1000).promptId(prompt.getId()).score(0.9)
						.completedAt(Instant.now()).build())
				.toList();
	}

	private String toJson(Object value) {
		try {
			return objectMapper.writeValueAsString(value);
		} catch (Exception ex) {
			throw new RuntimeException(ex);
		}
	}
}
