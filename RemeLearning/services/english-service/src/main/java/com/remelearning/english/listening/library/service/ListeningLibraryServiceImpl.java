package com.remelearning.english.listening.library.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.remelearning.common.exception.BusinessException;
import com.remelearning.common.storage.StorageClient;
import com.remelearning.english.listening.library.domain.ListeningLibraryAttempt;
import com.remelearning.english.listening.library.domain.ListeningLibraryQuestion;
import com.remelearning.english.listening.library.domain.ListeningLibrarySection;
import com.remelearning.english.listening.library.domain.ListeningLibraryTopic;
import com.remelearning.english.listening.library.domain.ListeningTopicProgress;
import com.remelearning.english.listening.library.domain.ListeningTopicStatus;
import com.remelearning.english.listening.library.dto.ListeningLibrarySectionDto;
import com.remelearning.english.listening.library.dto.ListeningLibraryTopicDto;
import com.remelearning.english.listening.library.dto.SubmitListeningAnswersRequest;
import com.remelearning.english.listening.library.dto.SubmitListeningAnswersResponse;
import com.remelearning.english.listening.library.generator.LlmListeningLibraryGenerator;
import com.remelearning.english.listening.library.mapper.ListeningLibraryAttemptMapper;
import com.remelearning.english.listening.library.mapper.ListeningLibraryQuestionMapper;
import com.remelearning.english.listening.library.mapper.ListeningLibrarySectionMapper;
import com.remelearning.english.listening.library.mapper.ListeningLibraryTopicMapper;
import com.remelearning.english.listening.library.mapper.ListeningTopicProgressMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Fixed-topic listening library: exposes topic progress (gating cloned from
 * {@code GrammarLibraryServiceImpl}'s LOCKED/UNLOCKED/IN_PROGRESS/PASSED state machine), starts/
 * resumes a Section (generating one via AI when the topic has no section yet), scores submitted
 * answers, and unlocks the next topic on pass.
 */
@Service
public class ListeningLibraryServiceImpl implements ListeningLibraryService {

	private static final double PASS_THRESHOLD = 0.7;
	private static final int FIRST_SEQUENCE_ORDER = 1;

	private final ListeningLibraryTopicMapper topicMapper;
	private final ListeningLibrarySectionMapper sectionMapper;
	private final ListeningLibraryQuestionMapper questionMapper;
	private final ListeningTopicProgressMapper progressMapper;
	private final ListeningLibraryAttemptMapper attemptMapper;
	private final LlmListeningLibraryGenerator generator;
	private final StorageClient storageClient;
	private final ObjectMapper objectMapper = new ObjectMapper();

	public ListeningLibraryServiceImpl(
			ListeningLibraryTopicMapper topicMapper,
			ListeningLibrarySectionMapper sectionMapper,
			ListeningLibraryQuestionMapper questionMapper,
			ListeningTopicProgressMapper progressMapper,
			ListeningLibraryAttemptMapper attemptMapper,
			LlmListeningLibraryGenerator generator,
			StorageClient storageClient) {
		this.topicMapper = topicMapper;
		this.sectionMapper = sectionMapper;
		this.questionMapper = questionMapper;
		this.progressMapper = progressMapper;
		this.attemptMapper = attemptMapper;
		this.generator = generator;
		this.storageClient = storageClient;
	}

	// Bootstraps the very first topic to UNLOCKED for a new learner, then reports every catalog
	// topic with whatever progress row exists (LOCKED for any topic without one yet) - mirrors
	// GrammarLibraryServiceImpl.listTopics exactly.
	@Override
	@Transactional
	public java.util.List<ListeningLibraryTopicDto> getTopics(String userId) {
		ListeningLibraryTopic firstTopic = topicMapper.findBySequenceOrder(FIRST_SEQUENCE_ORDER);
		if (firstTopic != null) {
			progressMapper.bootstrapFirstTopic(userId, firstTopic.getId());
		}
		Map<Long, ListeningTopicStatus> statusByTopicId = new HashMap<>();
		for (ListeningTopicProgress progress : progressMapper.findByUserId(userId)) {
			statusByTopicId.put(progress.getTopicId(), progress.getStatus());
		}
		return topicMapper.findAll().stream()
				.map(t -> new ListeningLibraryTopicDto(
						t.getId(), t.getName(), t.getLevel(),
						statusByTopicId.getOrDefault(t.getId(), ListeningTopicStatus.LOCKED).name()))
				.toList();
	}

	// Starts a brand-new Section (generated via AI) the first time a topic is reached, or resumes
	// its most recent Section on any later call - then marks the topic IN_PROGRESS.
	@Override
	@Transactional
	public ListeningLibrarySectionDto startOrResumeSection(String userId, Long topicId) {
		requireUnlockedOrInProgress(userId, topicId);
		ListeningLibraryTopic topic = topicMapper.findById(topicId);
		java.util.List<ListeningLibrarySection> existing = sectionMapper.findByTopicId(topicId);
		ListeningLibrarySection section = existing.isEmpty()
				? generator.generateSection(topic)
				: existing.get(existing.size() - 1);
		progressMapper.markInProgress(userId, topicId);

		java.util.List<ListeningLibraryQuestion> questions = questionMapper.findBySectionId(section.getId());
		java.util.List<ListeningLibrarySectionDto.QuestionView> questionViews = questions.stream()
				.map(q -> new ListeningLibrarySectionDto.QuestionView(
						q.getId(), q.getQuestionText(), parseOptions(q.getOptionsJson())))
				.toList();

		// StorageClient.url() returns a directly-fetchable locator for the stored audio object
		// (a real URL for a remote store, the key itself for local) - null if no audio was generated.
		String audioUrl = section.getAudioStorageKey() != null
				? storageClient.url(section.getAudioStorageKey())
				: null;

		return new ListeningLibrarySectionDto(section.getId(), section.getPassageText(), audioUrl, questionViews);
	}

	// Mirrors GrammarLibraryServiceImpl.requireUnlockedOrInProgress: only LOCKED is rejected (a
	// missing row counts as LOCKED); UNLOCKED/IN_PROGRESS/PASSED all pass through.
	private void requireUnlockedOrInProgress(String userId, Long topicId) {
		ListeningTopicProgress progress = progressMapper.findByUserIdAndTopicId(userId, topicId);
		ListeningTopicStatus status = progress == null ? ListeningTopicStatus.LOCKED : progress.getStatus();
		if (status == ListeningTopicStatus.LOCKED) {
			throw BusinessException.forbidden("Listening topic is locked for this learner: topicId=" + topicId);
		}
	}

	// Scores every submitted answer against the section's question pool, persists the attempt, and
	// - only on pass - marks the topic PASSED and unlocks the next topic (by sequence order),
	// re-reading progress after the upsert to report nextTopicUnlocked honestly.
	@Override
	@Transactional
	public SubmitListeningAnswersResponse submitAnswers(String userId, Long sectionId, SubmitListeningAnswersRequest req) {
		ListeningLibrarySection section = sectionMapper.findById(sectionId);
		java.util.List<ListeningLibraryQuestion> questions = questionMapper.findBySectionId(sectionId);
		Map<Long, String> correctByQuestionId = questions.stream()
				.collect(Collectors.toMap(ListeningLibraryQuestion::getId, ListeningLibraryQuestion::getCorrectOption));

		int correctCount = 0;
		for (SubmitListeningAnswersRequest.AnswerItem answer : req.getAnswers()) {
			if (Objects.equals(correctByQuestionId.get(answer.questionId()), answer.selectedOption())) {
				correctCount++;
			}
		}
		int total = questions.size();
		double score = total == 0 ? 0.0 : (double) correctCount / total;
		boolean passed = score >= PASS_THRESHOLD;

		ListeningLibraryAttempt attempt = new ListeningLibraryAttempt();
		attempt.setUserId(userId);
		attempt.setSectionId(sectionId);
		attempt.setScore(score);
		attempt.setCorrectCount(correctCount);
		attempt.setTotalQuestions(total);
		attempt.setStartedAt(OffsetDateTime.now().toInstant());
		attempt.setCompletedAt(OffsetDateTime.now().toInstant());
		attemptMapper.insert(attempt);

		Long nextTopicId = null;
		boolean nextTopicUnlocked = false;
		if (passed) {
			ListeningLibraryTopic topic = topicMapper.findById(section.getTopicId());
			progressMapper.markPassed(userId, topic.getId());
			ListeningLibraryTopic nextTopic = topicMapper.findBySequenceOrder(topic.getSequenceOrder() + 1);
			if (nextTopic != null) {
				progressMapper.unlockIfLocked(userId, nextTopic.getId());
				ListeningTopicProgress nextProgress = progressMapper.findByUserIdAndTopicId(userId, nextTopic.getId());
				nextTopicUnlocked = nextProgress != null && nextProgress.getStatus() != ListeningTopicStatus.LOCKED;
				nextTopicId = nextTopic.getId();
			}
		}

		return new SubmitListeningAnswersResponse(score, correctCount, total, passed, nextTopicId, nextTopicUnlocked);
	}

	@Override
	public java.util.List<ListeningLibraryAttempt> getHistory(String userId) {
		return attemptMapper.findByUserId(userId);
	}

	// Deserializes a question's stored JSON options array back into a plain string list.
	private java.util.List<String> parseOptions(String optionsJson) {
		if (optionsJson == null) {
			return java.util.List.of();
		}
		try {
			return objectMapper.readValue(optionsJson, new TypeReference<java.util.List<String>>() { });
		} catch (JsonProcessingException ex) {
			throw new IllegalStateException("Failed to deserialize listening library question options", ex);
		}
	}
}
