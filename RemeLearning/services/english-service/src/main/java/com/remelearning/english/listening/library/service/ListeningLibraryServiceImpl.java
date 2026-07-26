package com.remelearning.english.listening.library.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.remelearning.common.exception.BusinessException;
import com.remelearning.common.storage.AudioContentTypes;
import com.remelearning.common.storage.StorageClient;
import com.remelearning.english.listening.dto.ListeningAudioResource;
import com.remelearning.english.listening.dto.ListeningPracticeItemDto;
import com.remelearning.english.listening.generator.ListeningMistakeAnalyzer;
import com.remelearning.english.listening.library.domain.ListeningLibraryAttempt;
import com.remelearning.english.listening.library.domain.ListeningLibraryAttemptAnswer;
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
import com.remelearning.english.listening.library.mapper.ListeningLibraryAttemptAnswerMapper;
import com.remelearning.english.listening.library.mapper.ListeningLibraryAttemptMapper;
import com.remelearning.english.listening.library.mapper.ListeningLibraryQuestionMapper;
import com.remelearning.english.listening.library.mapper.ListeningLibrarySectionMapper;
import com.remelearning.english.listening.library.mapper.ListeningLibraryTopicMapper;
import com.remelearning.english.listening.library.mapper.ListeningTopicProgressMapper;
import com.remelearning.english.listening.service.ListeningLearnService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
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
	// A topic is now a chain of several Sections (bài nghe) the learner must pass in order, instead
	// of a single reused-forever Section - the chain length is random per topic (not global), see
	// targetSectionCount.
	private static final int MIN_SECTIONS_PER_TOPIC = 5;
	private static final int MAX_SECTIONS_PER_TOPIC = 10;
	// Must match bff-service's public route (LearnerController#getListeningLibraryAudio), not
	// english-service's own internal controller route - this URL is returned straight to the FE
	// client, which only ever talks to bff-service. Mirrors ListeningLearnServiceImpl.AUDIO_URL:
	// storageClient.url() alone isn't fetchable by a browser for local storage (returns the raw key)
	// nor for a private/non-presigned S3 bucket, so audio must be streamed through a backend proxy.
	private static final String AUDIO_URL = "/api/v1/learners/%s/learn/listening/library/sections/%d/audio";

	private final ListeningLibraryTopicMapper topicMapper;
	private final ListeningLibrarySectionMapper sectionMapper;
	private final ListeningLibraryQuestionMapper questionMapper;
	private final ListeningTopicProgressMapper progressMapper;
	private final ListeningLibraryAttemptMapper attemptMapper;
	private final ListeningLibraryAttemptAnswerMapper attemptAnswerMapper;
	private final LlmListeningLibraryGenerator generator;
	private final StorageClient storageClient;
	private final ListeningLearnService listeningLearnService;
	private final ObjectMapper objectMapper = new ObjectMapper();

	public ListeningLibraryServiceImpl(
			ListeningLibraryTopicMapper topicMapper,
			ListeningLibrarySectionMapper sectionMapper,
			ListeningLibraryQuestionMapper questionMapper,
			ListeningTopicProgressMapper progressMapper,
			ListeningLibraryAttemptMapper attemptMapper,
			ListeningLibraryAttemptAnswerMapper attemptAnswerMapper,
			LlmListeningLibraryGenerator generator,
			StorageClient storageClient,
			ListeningLearnService listeningLearnService) {
		this.topicMapper = topicMapper;
		this.sectionMapper = sectionMapper;
		this.questionMapper = questionMapper;
		this.progressMapper = progressMapper;
		this.attemptMapper = attemptMapper;
		this.attemptAnswerMapper = attemptAnswerMapper;
		this.generator = generator;
		this.storageClient = storageClient;
		this.listeningLearnService = listeningLearnService;
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

	// Starts the next not-yet-passed Section in the topic's chain (generating one via AI if the
	// chain hasn't reached its target length yet), or resumes a Section already started but not
	// passed - then marks the topic IN_PROGRESS. Once every Section in the chain has been passed,
	// falls back to returning the last one (review-only; submitAnswers no longer advances progress
	// past that point since passedAll is already true).
	@Override
	@Transactional
	public ListeningLibrarySectionDto startOrResumeSection(String userId, Long topicId) {
		ListeningLibraryTopic topic = requireTopic(topicId);
		requireUnlockedOrInProgress(userId, topicId);
		java.util.List<ListeningLibrarySection> existing = sectionMapper.findByTopicId(topicId);
		Set<Long> passed = passedSectionIds(userId, existing.stream().map(ListeningLibrarySection::getId).toList());
		Optional<ListeningLibrarySection> nextUnpassed = existing.stream()
				.filter(s -> !passed.contains(s.getId()))
				.findFirst();
		ListeningLibrarySection section;
		if (nextUnpassed.isPresent()) {
			section = nextUnpassed.get();
		} else if (existing.size() < targetSectionCount(topicId)) {
			section = generator.generateSection(topic);
		} else {
			section = existing.get(existing.size() - 1);
		}
		progressMapper.markInProgress(userId, topicId);

		java.util.List<ListeningLibraryQuestion> questions = questionMapper.findBySectionId(section.getId());
		java.util.List<ListeningLibrarySectionDto.QuestionView> questionViews = questions.stream()
				.map(q -> new ListeningLibrarySectionDto.QuestionView(
						q.getId(), q.getQuestionText(), parseOptions(q.getOptionsJson())))
				.toList();

		// Proxy path through this service's own streaming endpoint (see AUDIO_URL) rather than
		// storageClient.url() directly - that locator isn't reliably browser-fetchable (raw key for
		// local storage, non-presigned/internal-endpoint URL for S3). Null if no audio was generated.
		String audioUrl = section.getAudioStorageKey() != null
				? String.format(AUDIO_URL, userId, section.getId())
				: null;

		return new ListeningLibrarySectionDto(section.getId(), section.getPassageText(), audioUrl, questionViews);
	}

	// Mirrors GrammarLibraryServiceImpl.requireTopic: throws a 404-mapped BusinessException instead
	// of letting a null topic silently flow into the section generator.
	private ListeningLibraryTopic requireTopic(Long topicId) {
		ListeningLibraryTopic topic = topicMapper.findById(topicId);
		if (topic == null) {
			throw BusinessException.notFound("Listening library topic not found: id=" + topicId);
		}
		return topic;
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
		if (section == null) {
			throw BusinessException.notFound("Listening library section not found: id=" + sectionId);
		}
		// A missing `answers` field would otherwise NPE below; reject it as a clean 400 instead,
		// mirroring this class's other guard methods (requireTopic/requireUnlockedOrInProgress).
		if (req.getAnswers() == null) {
			throw BusinessException.badRequest("Submitted answers must not be null: sectionId=" + sectionId);
		}
		java.util.List<ListeningLibraryQuestion> questions = questionMapper.findBySectionId(sectionId);
		// The FE submits the full option text it displayed (SectionRunner.tsx has no A/B/C/D labels
		// to send back), while correctOption is stored as the LLM-authored letter ("A"-"D") - resolve
		// it to the matching option text here so comparison/persistence use the same representation.
		Map<Long, String> correctByQuestionId = questions.stream()
				.collect(Collectors.toMap(ListeningLibraryQuestion::getId, this::resolveCorrectOptionText));
		Map<Long, ListeningLibraryQuestion> questionById = questions.stream()
				.collect(Collectors.toMap(ListeningLibraryQuestion::getId, q -> q));

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

		// Persists each submitted answer against the now-known attempt id (only populated by
		// MyBatis' useGeneratedKeys after the insert above) so a later feature can regenerate AI
		// practice targeting exactly which questions were missed - mirrors dictation's mistake
		// history pattern. Runs as a second pass over req.getAnswers() rather than being folded
		// into the scoring loop above, since that loop runs before the attempt (and its id) exists.
		// Also builds the per-question breakdown returned to the FE so it can render a full
		// đúng/sai review list, not just the aggregate score.
		List<SubmitListeningAnswersResponse.QuestionResult> questionResults = new java.util.ArrayList<>();
		for (SubmitListeningAnswersRequest.AnswerItem answer : req.getAnswers()) {
			String correctOption = correctByQuestionId.get(answer.questionId());
			boolean isCorrect = Objects.equals(correctOption, answer.selectedOption());

			ListeningLibraryAttemptAnswer answerRow = new ListeningLibraryAttemptAnswer();
			answerRow.setAttemptId(attempt.getId());
			answerRow.setQuestionId(answer.questionId());
			answerRow.setSelectedOption(answer.selectedOption());
			answerRow.setCorrectOption(correctOption);
			answerRow.setIsCorrect(isCorrect);
			attemptAnswerMapper.insert(answerRow);

			ListeningLibraryQuestion question = questionById.get(answer.questionId());
			questionResults.add(new SubmitListeningAnswersResponse.QuestionResult(
					answer.questionId(),
					question != null ? question.getQuestionText() : null,
					question != null ? parseOptions(question.getOptionsJson()) : List.of(),
					answer.selectedOption(),
					correctOption,
					isCorrect));
		}

		Long nextTopicId = null;
		boolean nextTopicUnlocked = false;
		// A topic is now a chain of Sections - passing this one only unlocks the next topic once
		// every Section in the chain (up to its per-topic target length) has been passed, not on the
		// first individual Section pass.
		if (passed && hasPassedAllSections(userId, section.getTopicId())) {
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

		return new SubmitListeningAnswersResponse(
				score, correctCount, total, passed, nextTopicId, nextTopicUnlocked, questionResults);
	}

	@Override
	public java.util.List<ListeningLibraryAttempt> getHistory(String userId) {
		return attemptMapper.findByUserId(userId);
	}

	// Looks up the section's owning topicId directly via the mapper rather than getTopics/getHistory
	// - a section row already carries topicId (see ListeningLibrarySection), so no join/extra table
	// is needed. Returns null (not a thrown 404) for an unknown sectionId since this only backs a
	// best-effort deep-link resolution, not a user-facing endpoint of its own.
	@Override
	public Long resolveTopicId(Long sectionId) {
		ListeningLibrarySection section = sectionMapper.findById(sectionId);
		return section == null ? null : section.getTopicId();
	}

	// Generates AI practice targeted at this learner's own most recent completed attempt on one
	// section's missed questions: finds that attempt (there is no dedicated "by section" query, so
	// this filters the learner's own attempts and keeps the latest by completedAt), checks (via the
	// pure ListeningMistakeAnalyzer) whether it had any wrong answer at all. A Listening Library
	// section is scoped to exactly one topic and its questions carry no per-question topic tag of
	// their own - there is nothing more specific to target than that topic itself, exactly like
	// GrammarLibraryServiceImpl.generatePracticeFromSession - so every miss means the SAME topic,
	// and the topic's name is the single target-keyword fed into
	// ListeningLearnService.generatePracticeForKeywords (landing in the exact same
	// listening_practice_items bank the learn flow uses). No completed attempt, or no mistakes in
	// the latest one, both mean nothing to regenerate.
	@Override
	@Transactional
	public java.util.List<ListeningPracticeItemDto> generatePracticeFromSection(String userId, Long sectionId) {
		ListeningLibrarySection section = sectionMapper.findById(sectionId);
		if (section == null) {
			throw BusinessException.notFound("Listening library section not found: id=" + sectionId);
		}
		ListeningLibraryAttempt latestAttempt = findLatestAttemptForSection(userId, sectionId);
		if (latestAttempt == null) {
			return List.of();
		}
		List<ListeningLibraryAttemptAnswer> answers = attemptAnswerMapper.findByAttemptId(latestAttempt.getId());
		if (!ListeningMistakeAnalyzer.hasAnyMissedQuestion(answers)) {
			return List.of();
		}
		ListeningLibraryTopic topic = requireTopic(section.getTopicId());
		return listeningLearnService.generatePracticeForKeywords(userId, List.of(topic.getName()), topic.getLevel(), null);
	}

	// This learner's most recently completed attempt on this specific section, or null if they
	// have none yet - attemptMapper only exposes findByUserId, so the section filter and
	// most-recent selection both happen here rather than via a dedicated mapper query.
	private ListeningLibraryAttempt findLatestAttemptForSection(String userId, Long sectionId) {
		return attemptMapper.findByUserId(userId).stream()
				.filter(a -> sectionId.equals(a.getSectionId()))
				.max(Comparator.comparing(ListeningLibraryAttempt::getCompletedAt))
				.orElse(null);
	}

	// How many Sections (bài nghe) this topic's chain must have before it can be fully passed.
	// Deterministic from topicId (not stored, no migration needed) so the same topic always reports
	// the same target across calls, chosen once and stable rather than drifting between requests.
	private int targetSectionCount(Long topicId) {
		int span = MAX_SECTIONS_PER_TOPIC - MIN_SECTIONS_PER_TOPIC + 1;
		return MIN_SECTIONS_PER_TOPIC + (int) (Math.abs(topicId) % span);
	}

	// This learner's passed (score >= PASS_THRESHOLD) attempts, restricted to the given sectionIds -
	// attemptMapper only exposes findByUserId, so the filter happens here, mirroring
	// findLatestAttemptForSection's fetch-all-then-filter pattern.
	private Set<Long> passedSectionIds(String userId, List<Long> sectionIds) {
		Set<Long> scoped = Set.copyOf(sectionIds);
		return attemptMapper.findByUserId(userId).stream()
				.filter(a -> a.getScore() >= PASS_THRESHOLD)
				.map(ListeningLibraryAttempt::getSectionId)
				.filter(scoped::contains)
				.collect(Collectors.toSet());
	}

	// True once the learner has passed every Section currently in the topic's chain AND the chain
	// has reached its full target length - a topic can't be completed while it still has fewer
	// Sections than its target, even if every existing one has been passed.
	private boolean hasPassedAllSections(String userId, Long topicId) {
		List<ListeningLibrarySection> allSections = sectionMapper.findByTopicId(topicId);
		if (allSections.size() < targetSectionCount(topicId)) {
			return false;
		}
		Set<Long> passed = passedSectionIds(userId, allSections.stream().map(ListeningLibrarySection::getId).toList());
		return allSections.stream().allMatch(s -> passed.contains(s.getId()));
	}

	// Streams a section's synthesized audio, mirroring ListeningLearnServiceImpl.loadAudio -
	// the same StorageClient-backed WAV bytes, just keyed by section instead of practice item.
	@Override
	public ListeningAudioResource loadSectionAudio(Long sectionId) {
		ListeningLibrarySection section = sectionMapper.findById(sectionId);
		if (section == null) {
			throw BusinessException.notFound("Listening library section not found: id=" + sectionId);
		}
		if (section.getAudioStorageKey() == null) {
			throw BusinessException.notFound("Listening library section audio not ready: id=" + sectionId);
		}
		return new ListeningAudioResource(
				storageClient.read(section.getAudioStorageKey()),
				storageClient.size(section.getAudioStorageKey()),
				AudioContentTypes.contentType(section.getAudioStorageKey()),
				"listening-library-" + sectionId + AudioContentTypes.extension(section.getAudioStorageKey()));
	}

	// Resolves a question's stored correctOption letter ("A"-"D") to the matching option text
	// (A=1st entry, ...), matching LlmListeningLibraryGenerator's documented convention. Falls back
	// to the raw stored value if the letter is missing/out of range so a malformed row degrades to
	// the old (broken) comparison instead of throwing mid-request.
	private String resolveCorrectOptionText(ListeningLibraryQuestion question) {
		String letter = question.getCorrectOption();
		if (letter == null || letter.isBlank()) {
			return letter;
		}
		int index = Character.toUpperCase(letter.trim().charAt(0)) - 'A';
		java.util.List<String> options = parseOptions(question.getOptionsJson());
		if (index < 0 || index >= options.size()) {
			return letter;
		}
		return options.get(index);
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
