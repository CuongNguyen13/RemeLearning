package com.remelearning.english.grammar.library.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.remelearning.common.constants.LearningCategories;
import com.remelearning.common.exception.BusinessException;
import com.remelearning.common.scoring.ExactMatchScoreResult;
import com.remelearning.common.scoring.ExactMatchScorer;
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
import com.remelearning.english.grammar.library.dto.GrammarLibraryExampleDto;
import com.remelearning.english.grammar.library.dto.GrammarLibraryHistoryEntryDto;
import com.remelearning.english.grammar.library.dto.GrammarLibraryQuestionDto;
import com.remelearning.english.grammar.library.dto.GrammarLibraryTopicDto;
import com.remelearning.english.grammar.library.dto.GrammarSessionQuestionDto;
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
import com.remelearning.english.grammar.learn.generator.GrammarMistakeAnalyzer;
import com.remelearning.english.grammar.learn.service.GrammarLearnService;
import com.remelearning.english.practice.dto.PracticeAttemptRequest;
import com.remelearning.english.practice.dto.PracticeRedoRequest;
import com.remelearning.english.practice.service.PracticeService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Orchestrates the Grammar Library skill: 60 fixed catalog topics, each with an AI-generated
 * theory page + question pool (generated once, reused forever), a session-based quiz run, and a
 * pass/retry/unlock-next-topic progression per learner. Structurally mirrors
 * {@code VocabularyLibraryServiceImpl} (topic catalog + on-demand AI content) crossed with
 * {@code GrammarLearnServiceImpl} (grammar question types + weak-point feeding) — see those two
 * classes for the patterns being reused here.
 */
@Service
@RequiredArgsConstructor
public class GrammarLibraryServiceImpl implements GrammarLibraryService {

	private static final String ITEM_ID_PREFIX = "grammar:";
	private static final int FIRST_SEQUENCE_ORDER = 1;

	private final GrammarLibraryTopicMapper topicMapper;
	private final GrammarLibraryContentMapper contentMapper;
	private final GrammarLibraryQuestionMapper questionMapper;
	private final GrammarTopicProgressMapper progressMapper;
	private final GrammarLibrarySessionMapper sessionMapper;
	private final GrammarLibraryContentGenerator generator;
	private final PracticeService practiceService;
	private final ObjectMapper objectMapper;
	private final GrammarLearnService grammarLearnService;

	// Bootstraps the very first topic to UNLOCKED for a new learner, then reports every catalog
	// topic with whatever progress row exists (LOCKED for any topic without one yet).
	@Override
	@Transactional
	public List<GrammarLibraryTopicDto> listTopics(String userId) {
		GrammarLibraryTopic firstTopic = topicMapper.findBySequenceOrder(FIRST_SEQUENCE_ORDER);
		if (firstTopic != null) {
			progressMapper.bootstrapFirstTopic(userId, firstTopic.getId());
		}
		Map<Long, GrammarTopicStatus> statusByTopicId = new HashMap<>();
		for (GrammarTopicProgress progress : progressMapper.findByUserId(userId)) {
			statusByTopicId.put(progress.getTopicId(), progress.getStatus());
		}
		return topicMapper.findAll().stream()
				.map(topic -> GrammarLibraryTopicDto.builder()
						.topicId(topic.getId()).code(topic.getCode()).name(topic.getName())
						.description(topic.getDescription()).level(topic.getLevel())
						.sequenceOrder(topic.getSequenceOrder())
						.status(statusByTopicId.getOrDefault(topic.getId(), GrammarTopicStatus.LOCKED))
						.build())
				.toList();
	}

	// Generates the theory page + question pool via AI on first read only; every subsequent read
	// just re-serves the persisted rows, so the LLM is called exactly once per topic ever.
	@Override
	@Transactional
	public GrammarLibraryContentDto getTopicContent(Long topicId) {
		GrammarLibraryTopic topic = requireTopic(topicId);
		GrammarLibraryContent content = contentMapper.findByTopicId(topicId);
		if (content == null) {
			content = generateAndPersistContent(topic);
		}
		List<GrammarLibraryQuestion> questions = questionMapper.findByTopicId(topicId);
		return toContentDto(topic, content, questions);
	}

	@Override
	@Transactional
	public StartGrammarSessionResponse startSession(String userId, Long topicId) {
		GrammarLibraryTopic topic = requireTopic(topicId);
		requireUnlockedOrInProgress(userId, topicId);
		ensureContentGenerated(topic);
		List<GrammarLibraryQuestion> pool = questionMapper.findByTopicId(topicId);

		List<GrammarLibrarySessionQuestion> sessionQuestions = new ArrayList<>();
		for (GrammarLibraryQuestion question : pool) {
			sessionQuestions.add(GrammarLibrarySessionQuestion.builder()
					.questionRef("q-" + question.getId())
					.type(question.getQuestionType())
					.prompt(question.getPrompt())
					.options(readOptions(question.getOptionsJson()))
					.answer(question.getAnswer())
					.explanationVi(question.getExplanationVi())
					.translationVi(question.getTranslationVi())
					.build());
		}

		GrammarLibrarySession session = GrammarLibrarySession.builder()
				.userId(userId).topicId(topicId).sessionType(GrammarSessionType.INITIAL)
				.questionsJson(writeJson(sessionQuestions)).status(GrammarSessionStatus.IN_PROGRESS)
				.correctCount(0).totalCount(sessionQuestions.size())
				.build();
		sessionMapper.insertSession(session);
		progressMapper.markInProgress(userId, topicId);

		return toStartResponse(session, sessionQuestions);
	}

	@Override
	@Transactional
	public GrammarLibraryAnswerResultDto submitAnswer(Long sessionId, SubmitGrammarLibraryAnswerRequest request) {
		GrammarLibrarySession session = requireInProgressSession(sessionId);
		List<GrammarLibrarySessionQuestion> questions = readSessionQuestions(session.getQuestionsJson());
		GrammarLibrarySessionQuestion question = questions.stream()
				.filter(q -> q.getQuestionRef().equals(request.getQuestionRef()))
				.findFirst()
				.orElseThrow(() -> BusinessException.badRequest(
						"Unknown questionRef '" + request.getQuestionRef() + "' for session id=" + sessionId));

		boolean correct = scoreAnswer(question.getAnswer(), request.getSubmittedAnswer());
		sessionMapper.insertAnswer(GrammarLibrarySessionAnswer.builder()
				.sessionId(sessionId).questionRef(question.getQuestionRef())
				.submittedAnswer(request.getSubmittedAnswer()).correct(correct).build());

		return GrammarLibraryAnswerResultDto.builder()
				.questionRef(question.getQuestionRef()).correct(correct)
				.correctAnswer(question.getAnswer()).explanationVi(question.getExplanationVi())
				.translationVi(question.getTranslationVi())
				.build();
	}

	// Recomputes every question's final correctness (unanswered = wrong), feeds all of them into
	// the weak-point pipeline, then either marks the topic PASSED + unlocks the next one, or builds
	// a fresh RETRY session covering only the questions still wrong.
	@Override
	@Transactional
	public FinishGrammarLibrarySessionResponse finishSession(Long sessionId) {
		GrammarLibrarySession session = requireInProgressSession(sessionId);
		GrammarLibraryTopic topic = requireTopic(session.getTopicId());
		List<GrammarLibrarySessionQuestion> questions = readSessionQuestions(session.getQuestionsJson());
		Map<String, Boolean> correctByRef = latestCorrectnessByRef(sessionMapper.findAnswersBySessionId(sessionId));

		List<GrammarLibrarySessionQuestion> wrongQuestions = new ArrayList<>();
		int correctCount = 0;
		for (GrammarLibrarySessionQuestion question : questions) {
			boolean correct = correctByRef.getOrDefault(question.getQuestionRef(), false);
			if (correct) {
				correctCount++;
			} else {
				wrongQuestions.add(question);
			}
		}
		sessionMapper.completeSession(sessionId, correctCount, questions.size());
		feedWeakPoints(session.getUserId(), topic, questions, correctByRef);

		if (wrongQuestions.isEmpty()) {
			return buildPassedResponse(session, topic, correctCount, questions.size());
		}
		return buildRetryResponse(session, topic, wrongQuestions, correctCount, questions.size());
	}

	@Override
	public List<GrammarLibraryHistoryEntryDto> getHistory(String userId, Long topicId) {
		return sessionMapper.findCompletedByUserIdAndTopicId(userId, topicId).stream()
				.map(session -> GrammarLibraryHistoryEntryDto.builder()
						.sessionId(session.getId()).sessionType(session.getSessionType())
						.correctCount(session.getCorrectCount()).totalCount(session.getTotalCount())
						.accuracy(session.getTotalCount() == 0 ? 0.0 : (double) session.getCorrectCount() / session.getTotalCount())
						.completedAt(session.getCompletedAt())
						.build())
				.toList();
	}

	// Generates AI practice targeted at one past library session's missed questions: verifies the
	// session belongs to this learner, then checks (via the pure GrammarMistakeAnalyzer) whether the
	// session had any wrong answer at all. A Grammar Library session is scoped to exactly one topic
	// (one grammar rule concept) and its questions carry no per-question rule tag of their own, so
	// there is nothing more specific to target than that topic itself - every miss in the session
	// means the SAME topic's rule, so the topic's name is the target-rules entry fed into
	// GrammarLearnService.generatePracticeForRules (landing in the exact same grammar_practice_items
	// bank the learn flow uses - there is only one AI-practice destination per domain). No mistakes
	// means nothing to regenerate, mirroring finishSession's all-correct -> no-retry-session path.
	@Override
	@Transactional
	public List<GrammarPracticeItemDto> generatePracticeFromSession(String userId, Long sessionId) {
		GrammarLibrarySession session = sessionMapper.findById(sessionId);
		if (session == null || !session.getUserId().equals(userId)) {
			throw BusinessException.notFound("Grammar library session not found: id=" + sessionId);
		}
		boolean hasMistakes = GrammarMistakeAnalyzer.hasAnyMissedQuestion(
				session.getQuestionsJson(), sessionMapper.findAnswersBySessionId(sessionId));
		if (!hasMistakes) {
			return List.of();
		}
		GrammarLibraryTopic topic = requireTopic(session.getTopicId());
		return grammarLearnService.generatePracticeForRules(userId, List.of(topic.getName()), topic.getLevel(), null);
	}

	// --- helpers ---

	private GrammarLibraryContent generateAndPersistContent(GrammarLibraryTopic topic) {
		GeneratedGrammarTopicContent generated = generator.generateTopicContent(topic.getName(), topic.getLevel());
		GrammarLibraryContent content = GrammarLibraryContent.builder()
				.topicId(topic.getId())
				.explanationEn(generated.explanationEn()).explanationVi(generated.explanationVi())
				.illustrationText(generated.illustrationText())
				.examplesJson(writeJson(generated.examples()))
				.build();
		contentMapper.insert(content);
		for (GrammarLibraryQuestionSeed seed : generated.questions()) {
			questionMapper.insert(GrammarLibraryQuestion.builder()
					.topicId(topic.getId()).questionType(seed.type()).prompt(seed.prompt())
					.optionsJson(seed.options() == null ? null : writeJson(seed.options()))
					.answer(seed.answer()).explanationVi(seed.explanationVi()).translationVi(seed.translationVi())
					.build());
		}
		return content;
	}

	private void ensureContentGenerated(GrammarLibraryTopic topic) {
		if (contentMapper.findByTopicId(topic.getId()) == null) {
			generateAndPersistContent(topic);
		}
	}

	private void requireUnlockedOrInProgress(String userId, Long topicId) {
		GrammarTopicProgress progress = progressMapper.findByUserIdAndTopicId(userId, topicId);
		GrammarTopicStatus status = progress == null ? GrammarTopicStatus.LOCKED : progress.getStatus();
		if (status == GrammarTopicStatus.LOCKED) {
			throw BusinessException.forbidden("Grammar topic is locked for this learner: topicId=" + topicId);
		}
	}

	// Every question is scored against the pipeline with its own per-question correctness (not
	// deduped), the same convention VocabularyLibraryServiceImpl.feedWeakPoints uses for its
	// Section answers - lets PracticeServiceImpl detect in-batch recurrence.
	private void feedWeakPoints(String userId, GrammarLibraryTopic topic, List<GrammarLibrarySessionQuestion> questions,
			Map<String, Boolean> correctByRef) {
		List<PracticeAttemptRequest> attempts = new ArrayList<>();
		for (GrammarLibrarySessionQuestion question : questions) {
			PracticeAttemptRequest attempt = new PracticeAttemptRequest();
			attempt.setItemId(ITEM_ID_PREFIX + topic.getCode());
			attempt.setCategory(LearningCategories.GRAMMAR);
			attempt.setLabel(topic.getName());
			attempt.setCorrect(correctByRef.getOrDefault(question.getQuestionRef(), false));
			attempts.add(attempt);
		}
		if (attempts.isEmpty()) {
			return;
		}
		PracticeRedoRequest request = new PracticeRedoRequest();
		request.setUserId(userId);
		request.setAttempts(attempts);
		practiceService.redo(request);
	}

	private FinishGrammarLibrarySessionResponse buildPassedResponse(GrammarLibrarySession session, GrammarLibraryTopic topic,
			int correctCount, int totalCount) {
		progressMapper.markPassed(session.getUserId(), topic.getId());
		GrammarLibraryTopic nextTopic = topicMapper.findBySequenceOrder(topic.getSequenceOrder() + 1);
		boolean unlocked = false;
		if (nextTopic != null) {
			progressMapper.unlockIfLocked(session.getUserId(), nextTopic.getId());
			GrammarTopicProgress nextProgress = progressMapper.findByUserIdAndTopicId(session.getUserId(), nextTopic.getId());
			unlocked = nextProgress != null && nextProgress.getStatus() != GrammarTopicStatus.LOCKED;
		}
		return FinishGrammarLibrarySessionResponse.builder()
				.sessionId(session.getId()).correctCount(correctCount).totalCount(totalCount).passed(true)
				.retrySession(null).nextTopicUnlocked(unlocked)
				.nextTopicId(nextTopic == null ? null : nextTopic.getId())
				.build();
	}

	// Generates one fresh AI question per wrong question (same type, never repeating the old
	// prompt) and starts a new RETRY session made only of those - the topic stays IN_PROGRESS.
	private FinishGrammarLibrarySessionResponse buildRetryResponse(GrammarLibrarySession session, GrammarLibraryTopic topic,
			List<GrammarLibrarySessionQuestion> wrongQuestions, int correctCount, int totalCount) {
		List<GrammarLibrarySessionQuestion> retryQuestions = new ArrayList<>();
		for (int i = 0; i < wrongQuestions.size(); i++) {
			GrammarLibrarySessionQuestion wrong = wrongQuestions.get(i);
			GrammarLibraryQuestionSeed seed = generator.generateRetryQuestion(topic.getName(), topic.getLevel(), wrong.getType(), wrong.getPrompt());
			retryQuestions.add(GrammarLibrarySessionQuestion.builder()
					.questionRef("r-" + i).type(seed.type()).prompt(seed.prompt())
					.options(seed.options()).answer(seed.answer()).explanationVi(seed.explanationVi())
					.translationVi(seed.translationVi())
					.build());
		}
		GrammarLibrarySession retrySession = GrammarLibrarySession.builder()
				.userId(session.getUserId()).topicId(topic.getId()).sessionType(GrammarSessionType.RETRY)
				.questionsJson(writeJson(retryQuestions)).status(GrammarSessionStatus.IN_PROGRESS)
				.correctCount(0).totalCount(retryQuestions.size())
				.build();
		sessionMapper.insertSession(retrySession);

		return FinishGrammarLibrarySessionResponse.builder()
				.sessionId(session.getId()).correctCount(correctCount).totalCount(totalCount).passed(false)
				.retrySession(toStartResponse(retrySession, retryQuestions))
				.nextTopicUnlocked(false).nextTopicId(null)
				.build();
	}

	// Keeps only the most recently submitted correctness per questionRef - findAnswersBySessionId
	// returns oldest-first, so a later put() in iteration order wins on a re-submitted answer.
	private Map<String, Boolean> latestCorrectnessByRef(List<GrammarLibrarySessionAnswer> answers) {
		Map<String, Boolean> result = new LinkedHashMap<>();
		for (GrammarLibrarySessionAnswer answer : answers) {
			result.put(answer.getQuestionRef(), answer.isCorrect());
		}
		return result;
	}

	private boolean scoreAnswer(String correctAnswer, String submittedAnswer) {
		ExactMatchScoreResult result = ExactMatchScorer.score(List.of(correctAnswer), List.of(submittedAnswer), true);
		return Boolean.TRUE.equals(result.getPerQuestionCorrect().get(0));
	}

	private StartGrammarSessionResponse toStartResponse(GrammarLibrarySession session, List<GrammarLibrarySessionQuestion> questions) {
		List<GrammarSessionQuestionDto> questionDtos = questions.stream()
				.map(q -> GrammarSessionQuestionDto.builder()
						.questionRef(q.getQuestionRef()).type(q.getType()).prompt(q.getPrompt()).options(q.getOptions())
						.build())
				.toList();
		return StartGrammarSessionResponse.builder()
				.sessionId(session.getId()).sessionType(session.getSessionType())
				.questions(questionDtos).totalCount(questions.size())
				.build();
	}

	private GrammarLibraryContentDto toContentDto(GrammarLibraryTopic topic, GrammarLibraryContent content, List<GrammarLibraryQuestion> questions) {
		List<GrammarLibraryExampleDto> examples = readExamples(content.getExamplesJson()).stream()
				.map(e -> GrammarLibraryExampleDto.builder().en(e.getEn()).vi(e.getVi()).build())
				.toList();
		List<GrammarLibraryQuestionDto> questionDtos = questions.stream()
				.map(q -> GrammarLibraryQuestionDto.builder()
						.questionId(q.getId()).type(q.getQuestionType()).prompt(q.getPrompt())
						.options(readOptions(q.getOptionsJson())).answer(q.getAnswer()).explanationVi(q.getExplanationVi())
						.translationVi(q.getTranslationVi())
						.build())
				.toList();
		return GrammarLibraryContentDto.builder()
				.topicId(topic.getId()).explanationEn(content.getExplanationEn()).explanationVi(content.getExplanationVi())
				.illustrationText(content.getIllustrationText()).examples(examples).questions(questionDtos)
				.build();
	}

	private GrammarLibraryTopic requireTopic(Long topicId) {
		GrammarLibraryTopic topic = topicMapper.findById(topicId);
		if (topic == null) {
			throw BusinessException.notFound("Grammar library topic not found: id=" + topicId);
		}
		return topic;
	}

	private GrammarLibrarySession requireInProgressSession(Long sessionId) {
		GrammarLibrarySession session = sessionMapper.findById(sessionId);
		if (session == null) {
			throw BusinessException.notFound("Grammar library session not found: id=" + sessionId);
		}
		if (session.getStatus() != GrammarSessionStatus.IN_PROGRESS) {
			throw BusinessException.conflict("Grammar library session is not in progress: id=" + sessionId);
		}
		return session;
	}

	private List<String> readOptions(String json) {
		if (json == null) {
			return null;
		}
		try {
			return objectMapper.readValue(json, new TypeReference<List<String>>() { });
		} catch (JsonProcessingException ex) {
			throw new IllegalStateException("Failed to deserialize grammar library question options", ex);
		}
	}

	private List<GrammarLibraryExample> readExamples(String json) {
		try {
			return objectMapper.readValue(json, new TypeReference<List<GrammarLibraryExample>>() { });
		} catch (JsonProcessingException ex) {
			throw new IllegalStateException("Failed to deserialize grammar library examples", ex);
		}
	}

	private List<GrammarLibrarySessionQuestion> readSessionQuestions(String json) {
		try {
			return objectMapper.readValue(json, new TypeReference<List<GrammarLibrarySessionQuestion>>() { });
		} catch (JsonProcessingException ex) {
			throw new IllegalStateException("Failed to deserialize grammar library session questions", ex);
		}
	}

	private String writeJson(Object value) {
		try {
			return objectMapper.writeValueAsString(value);
		} catch (JsonProcessingException ex) {
			throw new IllegalStateException("Failed to serialize grammar library content", ex);
		}
	}
}
