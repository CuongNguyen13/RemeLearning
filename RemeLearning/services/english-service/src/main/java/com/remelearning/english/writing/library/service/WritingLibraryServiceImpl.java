package com.remelearning.english.writing.library.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.remelearning.common.exception.BusinessException;
import com.remelearning.english.writing.domain.WritingTaskType;
import com.remelearning.english.writing.dto.WritingPracticeItemDto;
import com.remelearning.english.writing.generator.WritingMistakeAnalyzer;
import com.remelearning.english.writing.grading.WritingErrorPipeline;
import com.remelearning.english.writing.grading.WritingGrade;
import com.remelearning.english.writing.grading.WritingGrader;
import com.remelearning.english.writing.library.domain.WritingLibraryAttempt;
import com.remelearning.english.writing.library.domain.WritingLibraryPrompt;
import com.remelearning.english.writing.library.domain.WritingLibraryTopic;
import com.remelearning.english.writing.library.domain.WritingTaxonomy;
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
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Fixed-catalogue writing library: reports topic progress along ONE taxonomy axis at a time (gating
 * cloned from {@code ListeningLibraryServiceImpl}'s LOCKED/UNLOCKED/IN_PROGRESS/PASSED state
 * machine), starts/resumes a prompt in the topic's chain (generating one via AI when the chain is
 * still short), grades submissions through the same {@link WritingGrader} the learn tab uses, and
 * unlocks the next topic on pass.
 *
 * <p>The one behavioural difference from every other library: everything axis-related is scoped to a
 * single {@link WritingTaxonomy}. Bootstrapping opens the first topic of each axis independently, and
 * "unlock the next topic" only ever looks within the passed topic's own axis - {@code sequence_order}
 * restarts at 1 per axis, so a global lookup would jump between axes.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WritingLibraryServiceImpl implements WritingLibraryService {

	private static final double PASS_THRESHOLD = 0.7;
	private static final int FIRST_SEQUENCE_ORDER = 1;
	// A topic is a chain of several prompts the learner must pass in order; the chain length varies
	// per topic rather than globally, matching ListeningLibraryServiceImpl's model.
	private static final int MIN_PROMPTS_PER_TOPIC = 3;
	private static final int MAX_PROMPTS_PER_TOPIC = 6;

	private final WritingLibraryTopicMapper topicMapper;
	private final WritingLibraryPromptMapper promptMapper;
	private final WritingTopicProgressMapper progressMapper;
	private final WritingLibraryAttemptMapper attemptMapper;
	private final WritingLibraryContentGenerator generator;
	private final WritingGrader grader;
	private final WritingErrorPipeline errorPipeline;
	private final WritingLearnService writingLearnService;
	private final ObjectMapper objectMapper;

	// Bootstraps the first topic OF THIS AXIS to UNLOCKED for a new learner, then reports every topic
	// on the axis with whatever progress row exists (LOCKED for any without one yet) plus how far
	// through its prompt chain they are.
	@Override
	@Transactional
	public List<WritingLibraryTopicDto> getTopics(String userId, String taxonomy) {
		String axis = requireTaxonomy(taxonomy).code();
		WritingLibraryTopic firstTopic = topicMapper.findByTaxonomyAndSequenceOrder(axis, FIRST_SEQUENCE_ORDER);
		if (firstTopic != null) {
			progressMapper.bootstrapFirstTopic(userId, firstTopic.getId());
		}
		Map<Long, WritingTopicStatus> statusByTopicId = new HashMap<>();
		for (WritingTopicProgress progress : progressMapper.findByUserId(userId)) {
			statusByTopicId.put(progress.getTopicId(), progress.getStatus());
		}
		Set<Long> passedPromptIds = passedPromptIds(userId);
		return topicMapper.findByTaxonomy(axis).stream()
				.map(topic -> {
					List<WritingLibraryPrompt> prompts = promptMapper.findByTopicId(topic.getId());
					long passedCount = prompts.stream().filter(p -> passedPromptIds.contains(p.getId())).count();
					return WritingLibraryTopicDto.builder()
							.topicId(topic.getId())
							.taxonomy(topic.getTaxonomy())
							.code(topic.getCode())
							.name(topic.getName())
							.description(topic.getDescription())
							.level(topic.getLevel())
							.sequenceOrder(topic.getSequenceOrder())
							.status(statusByTopicId.getOrDefault(topic.getId(), WritingTopicStatus.LOCKED).name())
							.passedPromptCount((int) passedCount)
							.targetPromptCount(targetPromptCount(topic.getId()))
							.build();
				})
				.toList();
	}

	// Serves the next prompt the learner still owes: the first not-yet-passed one already in the
	// chain, or a freshly generated one while the chain is shorter than its target. Once everything is
	// passed it hands back the last prompt for review only (submitAnswer no longer advances anything).
	@Override
	@Transactional
	public WritingLibraryPromptDto startOrResumePrompt(String userId, Long topicId, WritingTaskType taskType) {
		WritingLibraryTopic topic = requireTopic(topicId);
		requireUnlocked(userId, topicId);

		List<WritingLibraryPrompt> existing = promptMapper.findByTopicId(topicId);
		Set<Long> passed = passedPromptIds(userId);
		Optional<WritingLibraryPrompt> nextUnpassed = existing.stream()
				.filter(prompt -> !passed.contains(prompt.getId()))
				.findFirst();

		WritingLibraryPrompt prompt;
		if (nextUnpassed.isPresent()) {
			prompt = nextUnpassed.get();
		} else if (existing.size() < targetPromptCount(topicId)) {
			prompt = generator.generatePrompt(topic, taskType);
			existing = promptMapper.findByTopicId(topicId);
		} else {
			prompt = existing.get(existing.size() - 1);
		}
		progressMapper.markInProgress(userId, topicId);

		return toPromptDto(prompt, topic, positionOf(prompt, existing));
	}

	// Grades through the shared WritingGrader, persists the attempt with the grader's own
	// criteria/errors, routes the errors into the learner's grammar/vocabulary weak points via the
	// shared pipeline, then - only on a passing score that completes the whole chain - marks the topic
	// PASSED and unlocks the next topic ON THE SAME AXIS.
	@Override
	@Transactional
	public SubmitWritingLibraryAnswerResponse submitAnswer(
			String userId, Long promptId, SubmitWritingLibraryAnswerRequest request) {
		WritingLibraryPrompt prompt = requirePrompt(promptId);
		WritingLibraryTopic topic = requireTopic(prompt.getTopicId());
		Instant startedAt = Instant.now();

		WritingGrade grade = grader.grade(
				prompt.getTaskType(), prompt.getPromptText(), prompt.getReferenceAnswer(), request.getSubmittedText());
		double score = errorPipeline.averageCriteria(grade.criteria());
		boolean passed = score >= PASS_THRESHOLD;

		WritingLibraryAttempt attempt = WritingLibraryAttempt.builder()
				.userId(userId)
				.promptId(promptId)
				.submittedText(request.getSubmittedText())
				.correctedText(grade.correctedText())
				.score(score)
				.criteriaJson(writeJson(grade.criteria()))
				.errorsJson(writeJson(grade.errors()))
				.feedback(grade.feedbackVi())
				.startedAt(startedAt)
				.completedAt(Instant.now())
				.build();
		attemptMapper.insert(attempt);

		errorPipeline.feedWeakPoints(userId, grade.errors());

		List<WritingLibraryPrompt> chain = promptMapper.findByTopicId(topic.getId());
		Set<Long> passedAfter = passedPromptIds(userId);
		int passedCount = (int) chain.stream().filter(p -> passedAfter.contains(p.getId())).count();

		boolean topicPassed = false;
		Long nextTopicId = null;
		boolean nextTopicUnlocked = false;
		if (passed && hasCompletedChain(topic.getId(), chain, passedAfter)) {
			progressMapper.markPassed(userId, topic.getId());
			topicPassed = true;
			// Axis-scoped: sequence_order only orders topics within one taxonomy, so the next topic must
			// be looked up on this topic's own axis or unlocking would jump onto a different one.
			WritingLibraryTopic nextTopic = topicMapper.findByTaxonomyAndSequenceOrder(
					topic.getTaxonomy(), topic.getSequenceOrder() + 1);
			if (nextTopic != null) {
				progressMapper.unlockIfLocked(userId, nextTopic.getId());
				WritingTopicProgress nextProgress = progressMapper.findByUserIdAndTopicId(userId, nextTopic.getId());
				nextTopicId = nextTopic.getId();
				nextTopicUnlocked = nextProgress != null && nextProgress.getStatus() != WritingTopicStatus.LOCKED;
			}
		}

		return SubmitWritingLibraryAnswerResponse.builder()
				.attemptId(attempt.getId())
				.score(score)
				.passed(passed)
				.criteria(grade.criteria())
				.correctedText(grade.correctedText())
				.errors(grade.errors())
				.feedback(grade.feedbackVi())
				.referenceAnswer(prompt.getReferenceAnswer())
				.actionAdvice(errorPipeline.buildActionAdvice(grade.errors()))
				.passedPromptCount(passedCount)
				.targetPromptCount(targetPromptCount(topic.getId()))
				.topicPassed(topicPassed)
				.nextTopicId(nextTopicId)
				.nextTopicUnlocked(nextTopicUnlocked)
				.build();
	}

	// Regenerates "học thường" practice from a library attempt's own mistakes, reusing the learn tab's
	// generate-and-persist pipeline so the new prompt lands in the same writing_practice_items bank -
	// mirrors ListeningLibraryServiceImpl.generatePracticeFromSection. No mistakes means nothing to
	// regenerate.
	@Override
	@Transactional
	public List<WritingPracticeItemDto> generatePracticeFromAttempt(String userId, Long attemptId) {
		WritingLibraryAttempt attempt = attemptMapper.findByIdAndUserId(attemptId, userId);
		if (attempt == null) {
			throw BusinessException.notFound("Writing library attempt not found: id=" + attemptId);
		}
		List<String> mistakeLabels = WritingMistakeAnalyzer.extractMistakeLabels(attempt.getErrorsJson());
		if (mistakeLabels.isEmpty()) {
			return List.of();
		}
		WritingLibraryPrompt prompt = requirePrompt(attempt.getPromptId());
		WritingLibraryTopic topic = requireTopic(prompt.getTopicId());
		return writingLearnService.generatePracticeForLabels(
				userId, prompt.getTaskType(), mistakeLabels, topic.getLevel(), null);
	}

	// --- helpers ---

	// This learner's passed (score >= PASS_THRESHOLD) prompt ids. attemptMapper only exposes
	// findByUserId, so the threshold filter happens here - the same fetch-all-then-filter shape
	// ListeningLibraryServiceImpl.passedSectionIds uses.
	private Set<Long> passedPromptIds(String userId) {
		return attemptMapper.findByUserId(userId).stream()
				.filter(attempt -> attempt.getScore() >= PASS_THRESHOLD)
				.map(WritingLibraryAttempt::getPromptId)
				.collect(Collectors.toSet());
	}

	// True once the chain has reached its full target length AND every prompt in it is passed - a
	// topic can't be completed while it still has fewer prompts than its target, even if every
	// existing one has been passed.
	private boolean hasCompletedChain(Long topicId, List<WritingLibraryPrompt> chain, Set<Long> passed) {
		if (chain.size() < targetPromptCount(topicId)) {
			return false;
		}
		return chain.stream().allMatch(prompt -> passed.contains(prompt.getId()));
	}

	// How many prompts this topic's chain must hold before it can be fully passed. Derived from
	// topicId rather than stored, so the same topic always reports the same target across calls
	// without needing a migration - same trick as ListeningLibraryServiceImpl.targetSectionCount.
	private int targetPromptCount(Long topicId) {
		int span = MAX_PROMPTS_PER_TOPIC - MIN_PROMPTS_PER_TOPIC + 1;
		return MIN_PROMPTS_PER_TOPIC + (int) (Math.abs(topicId) % span);
	}

	private int positionOf(WritingLibraryPrompt prompt, List<WritingLibraryPrompt> chain) {
		for (int i = 0; i < chain.size(); i++) {
			if (chain.get(i).getId().equals(prompt.getId())) {
				return i + 1;
			}
		}
		return chain.size();
	}

	private WritingLibraryPromptDto toPromptDto(
			WritingLibraryPrompt prompt, WritingLibraryTopic topic, int position) {
		return WritingLibraryPromptDto.builder()
				.promptId(prompt.getId())
				.topicId(topic.getId())
				.topicName(topic.getName())
				.taskType(prompt.getTaskType())
				.promptText(prompt.getPromptText())
				.sourceLang(prompt.getTaskType().sourceLang())
				.targetLang(prompt.getTaskType().targetLang())
				.minWords(prompt.getMinWords())
				.position(position)
				.targetPromptCount(targetPromptCount(topic.getId()))
				.build();
	}

	// Rejects an unknown axis as a clean 400 rather than letting it reach the mapper and quietly
	// return an empty topic list, which would look like a missing catalogue.
	private WritingTaxonomy requireTaxonomy(String taxonomy) {
		try {
			return WritingTaxonomy.fromCode(taxonomy);
		} catch (IllegalArgumentException ex) {
			throw BusinessException.badRequest("Unknown writing library taxonomy: " + taxonomy);
		}
	}

	private WritingLibraryTopic requireTopic(Long topicId) {
		WritingLibraryTopic topic = topicMapper.findById(topicId);
		if (topic == null) {
			throw BusinessException.notFound("Writing library topic not found: id=" + topicId);
		}
		return topic;
	}

	private WritingLibraryPrompt requirePrompt(Long promptId) {
		WritingLibraryPrompt prompt = promptMapper.findById(promptId);
		if (prompt == null) {
			throw BusinessException.notFound("Writing library prompt not found: id=" + promptId);
		}
		return prompt;
	}

	// Only LOCKED is rejected (a missing row counts as LOCKED); UNLOCKED/IN_PROGRESS/PASSED all pass
	// through, so a learner can revisit a topic they already finished.
	private void requireUnlocked(String userId, Long topicId) {
		WritingTopicProgress progress = progressMapper.findByUserIdAndTopicId(userId, topicId);
		WritingTopicStatus status = progress == null ? WritingTopicStatus.LOCKED : progress.getStatus();
		if (status == WritingTopicStatus.LOCKED) {
			throw BusinessException.forbidden("Writing library topic is locked for this learner: topicId=" + topicId);
		}
	}

	private String writeJson(Object value) {
		try {
			return objectMapper.writeValueAsString(value);
		} catch (JsonProcessingException ex) {
			throw new IllegalStateException("Failed to serialize writing library attempt content", ex);
		}
	}

}
