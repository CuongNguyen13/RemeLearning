package com.remelearning.english.grammar.learn.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.remelearning.common.constants.LearningCategories;
import com.remelearning.common.exception.BusinessException;
import com.remelearning.english.grammar.domain.GrammarWeakPoint;
import com.remelearning.english.grammar.learn.domain.GrammarAttemptDetailRow;
import com.remelearning.english.grammar.learn.domain.GrammarAttemptHistoryRow;
import com.remelearning.english.grammar.learn.domain.GrammarPracticeAttempt;
import com.remelearning.english.grammar.learn.domain.GrammarPracticeItem;
import com.remelearning.english.grammar.learn.domain.GrammarQuestionItem;
import com.remelearning.english.grammar.learn.dto.GenerateGrammarPracticeRequest;
import com.remelearning.english.grammar.learn.dto.GrammarAttemptDetailDto;
import com.remelearning.english.grammar.learn.dto.GrammarAttemptHistoryEntryDto;
import com.remelearning.english.grammar.learn.dto.GrammarAttemptQuestionResultDto;
import com.remelearning.english.grammar.learn.dto.GrammarAttemptResultDto;
import com.remelearning.english.grammar.learn.dto.GrammarPracticeItemDto;
import com.remelearning.english.grammar.learn.dto.GrammarQuestionDto;
import com.remelearning.english.grammar.learn.dto.SubmitGrammarAttemptRequest;
import com.remelearning.english.grammar.learn.generator.GeneratedGrammarPractice;
import com.remelearning.english.grammar.learn.generator.GrammarMistakeAnalyzer;
import com.remelearning.english.grammar.learn.generator.GrammarPracticeGenerator;
import com.remelearning.english.grammar.learn.mapper.GrammarPracticeMapper;
import com.remelearning.english.grammar.learn.scoring.GrammarAttemptScorer;
import com.remelearning.english.grammar.learn.scoring.GrammarScoreResult;
import com.remelearning.english.grammar.service.GrammarWeakPointService;
import com.remelearning.english.practice.dto.PracticeAttemptRequest;
import com.remelearning.english.practice.dto.PracticeRedoRequest;
import com.remelearning.english.practice.service.PracticeService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Orchestrates the grammar "learn" skill - structurally identical to
 * {@code VocabLearnServiceImpl} (see that class for the full rationale): generating an AI practice
 * set targeting the learner's own weak grammar rules, grading a submitted attempt with the pure
 * {@link GrammarAttemptScorer}, and feeding each graded rule back into the existing
 * spaced-repetition/weak-point pipeline via {@link PracticeService#redo}.
 */
@Service
@RequiredArgsConstructor
public class GrammarLearnServiceImpl implements GrammarLearnService {

	private static final int DEFAULT_FOCUS_LIMIT = 8;
	private static final String ITEM_ID_PREFIX = "grammar:";

	private final GrammarPracticeMapper grammarPracticeMapper;
	private final GrammarPracticeGenerator generator;
	private final GrammarWeakPointService grammarWeakPointService;
	private final PracticeService practiceService;
	private final ObjectMapper objectMapper;

	@Override
	@Transactional
	public GrammarPracticeItemDto generate(String userId, GenerateGrammarPracticeRequest request) {
		List<String> targetRules = resolveTargetRules(userId, request.getFocusItems());
		GeneratedGrammarPractice generated = generator.generate(targetRules, request.getLevel(), request.getExamType());

		GrammarPracticeItem item = GrammarPracticeItem.builder()
				.userId(userId)
				.level(request.getLevel())
				.examType(request.getExamType())
				.topic(generated.topic())
				.targetRulesJson(writeJson(targetRules))
				.itemsJson(writeJson(generated.items()))
				.build();
		grammarPracticeMapper.insertItem(item);

		return toItemDto(item, targetRules, generated.items());
	}

	@Override
	public GrammarPracticeItemDto getItem(Long itemId) {
		GrammarPracticeItem item = requireItem(itemId);
		return toItemDto(item, readWords(item.getTargetRulesJson()), readItems(item.getItemsJson()));
	}

	@Override
	public List<GrammarPracticeItemDto> listItems(String userId) {
		return grammarPracticeMapper.findItemsByUserId(userId).stream()
				.map(item -> toItemDto(item, readWords(item.getTargetRulesJson()), readItems(item.getItemsJson())))
				.toList();
	}

	@Override
	@Transactional
	public GrammarAttemptResultDto submit(SubmitGrammarAttemptRequest request) {
		GrammarPracticeItem item = requireItem(request.getPracticeItemId());
		List<GrammarQuestionItem> questions = readItems(item.getItemsJson());

		GrammarScoreResult score = GrammarAttemptScorer.score(questions, request.getAnswers());

		GrammarPracticeAttempt attempt = GrammarPracticeAttempt.builder()
				.practiceItemId(item.getId())
				.userId(request.getUserId())
				.answersJson(writeJson(request.getAnswers()))
				.score(score.getAccuracy())
				.build();
		grammarPracticeMapper.insertAttempt(attempt);

		List<GrammarAttemptQuestionResultDto> results = buildQuestionResults(questions, request.getAnswers(), score);
		feedWeakPoints(request.getUserId(), questions, score);

		return GrammarAttemptResultDto.builder()
				.accuracy(score.getAccuracy())
				.results(results)
				.actionAdvice(buildActionAdvice(questions, score))
				.build();
	}

	@Override
	public List<GrammarAttemptHistoryEntryDto> getHistory(String userId) {
		return grammarPracticeMapper.findHistoryByUserId(userId).stream()
				.map(this::toHistoryEntryDto)
				.toList();
	}

	// Shared persistence step: builds one GrammarPracticeItem from whatever target rules/level/exam
	// type the caller resolved (top weak points, explicit focus items, or a past attempt's misses),
	// inserts it into the same grammar_practice_items bank `generate` uses, and returns the
	// learner's refreshed practice-set list.
	@Override
	@Transactional
	public List<GrammarPracticeItemDto> generatePracticeForRules(String userId, List<String> targetRules, String level, String examType) {
		GeneratedGrammarPractice generated = generator.generate(targetRules, level, examType);

		GrammarPracticeItem item = GrammarPracticeItem.builder()
				.userId(userId)
				.level(level)
				.examType(examType)
				.topic(generated.topic())
				.targetRulesJson(writeJson(targetRules))
				.itemsJson(writeJson(generated.items()))
				.build();
		grammarPracticeMapper.insertItem(item);

		return listItems(userId);
	}

	// Generates AI practice targeted at one past attempt's mistakes: verifies the attempt belongs to
	// this learner, diffs its stored questions against the stored answers via the pure
	// GrammarMistakeAnalyzer to find every missed rule, then reuses the exact same
	// generate-and-persist pipeline `generate` uses (generatePracticeForRules) so the regenerated
	// content lands in the same bank as a normal "học thường" set.
	@Override
	@Transactional
	public List<GrammarPracticeItemDto> generatePracticeFromAttempt(String userId, Long attemptId) {
		GrammarAttemptDetailRow attempt = grammarPracticeMapper.findAttemptDetailByIdAndUserId(attemptId, userId);
		if (attempt == null) {
			throw BusinessException.notFound("Grammar practice attempt not found: id=" + attemptId);
		}
		List<String> missedRules = GrammarMistakeAnalyzer.extractMissedRules(attempt.getItemsJson(), attempt.getAnswersJson());
		return generatePracticeForRules(userId, missedRules, attempt.getLevel(), attempt.getExamType());
	}

	@Override
	public GrammarAttemptDetailDto getAttemptDetail(String userId, Long attemptId) {
		GrammarAttemptDetailRow row = grammarPracticeMapper.findAttemptDetailByIdAndUserId(attemptId, userId);
		if (row == null) {
			throw BusinessException.notFound("Grammar practice attempt not found: id=" + attemptId);
		}
		List<GrammarQuestionItem> questions = readItems(row.getItemsJson());
		List<String> answers = readWords(row.getAnswersJson());
		GrammarScoreResult score = GrammarAttemptScorer.score(questions, answers);

		return GrammarAttemptDetailDto.builder()
				.attemptId(row.getAttemptId())
				.level(row.getLevel())
				.examType(row.getExamType())
				.topic(row.getTopic())
				.accuracy(row.getScore())
				.results(buildQuestionResults(questions, answers, score))
				.attemptedAt(row.getCreatedAt())
				.build();
	}

	// --- helpers ---

	private List<String> resolveTargetRules(String userId, List<String> focusItems) {
		if (focusItems != null && !focusItems.isEmpty()) {
			return focusItems;
		}
		return grammarWeakPointService.getTopWeakPoints(userId, DEFAULT_FOCUS_LIMIT).stream()
				.map(GrammarWeakPoint::getLabel)
				.toList();
	}

	// Scores each question directly against the existing practice/redo pipeline - see
	// VocabLearnServiceImpl.feedWeakPoints for the full rationale (mistake_history +
	// WeakPointScoringEngine update grammar_weak_points immediately, and the bundled
	// learning.gap.analysis.requested keeps recommendation-service/dashboard-service in sync).
	private void feedWeakPoints(String userId, List<GrammarQuestionItem> questions, GrammarScoreResult score) {
		List<PracticeAttemptRequest> attempts = new ArrayList<>();
		Set<String> seenRules = new LinkedHashSet<>();
		for (int i = 0; i < questions.size(); i++) {
			GrammarQuestionItem question = questions.get(i);
			if (!seenRules.add(question.getTargetRule().toLowerCase())) {
				continue;
			}
			PracticeAttemptRequest attempt = new PracticeAttemptRequest();
			attempt.setItemId(ITEM_ID_PREFIX + question.getTargetRule().toLowerCase());
			attempt.setCategory(LearningCategories.GRAMMAR);
			attempt.setLabel(question.getTargetRule());
			attempt.setCorrect(score.getPerQuestionCorrect().get(i));
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

	private List<String> buildActionAdvice(List<GrammarQuestionItem> questions, GrammarScoreResult score) {
		Set<String> advice = new LinkedHashSet<>();
		for (int i = 0; i < questions.size(); i++) {
			if (Boolean.TRUE.equals(score.getPerQuestionCorrect().get(i))) {
				continue;
			}
			GrammarQuestionItem question = questions.get(i);
			String translation = question.getTranslation();
			advice.add("Ôn lại quy tắc '%s'%s. Luyện viết thêm câu mới áp dụng quy tắc này."
					.formatted(question.getTargetRule(), translation == null ? "" : " (" + translation + ")"));
		}
		return new ArrayList<>(advice);
	}

	private List<GrammarAttemptQuestionResultDto> buildQuestionResults(
			List<GrammarQuestionItem> questions, List<String> answers, GrammarScoreResult score) {
		List<GrammarAttemptQuestionResultDto> results = new ArrayList<>();
		for (int i = 0; i < questions.size(); i++) {
			GrammarQuestionItem question = questions.get(i);
			results.add(GrammarAttemptQuestionResultDto.builder()
					.index(i)
					.prompt(question.getPrompt())
					.yourAnswer(i < answers.size() ? answers.get(i) : null)
					.correctAnswer(question.getAnswer())
					.correct(score.getPerQuestionCorrect().get(i))
					.translation(question.getTranslation())
					.translationVi(question.getTranslationVi())
					.build());
		}
		return results;
	}

	private GrammarPracticeItemDto toItemDto(GrammarPracticeItem item, List<String> targetRules, List<GrammarQuestionItem> questions) {
		List<GrammarQuestionDto> questionDtos = new ArrayList<>();
		for (int i = 0; i < questions.size(); i++) {
			GrammarQuestionItem question = questions.get(i);
			questionDtos.add(GrammarQuestionDto.builder()
					.index(i)
					.prompt(question.getPrompt())
					.type(question.getType())
					.options(question.getOptions())
					.answer(question.getAnswer())
					.translation(question.getTranslation())
					.translationVi(question.getTranslationVi())
					.build());
		}
		return GrammarPracticeItemDto.builder()
				.practiceItemId(item.getId())
				.level(item.getLevel())
				.examType(item.getExamType())
				.topic(item.getTopic())
				.targetRules(targetRules)
				.questions(questionDtos)
				.createdAt(item.getCreatedAt())
				.build();
	}

	private GrammarAttemptHistoryEntryDto toHistoryEntryDto(GrammarAttemptHistoryRow row) {
		return GrammarAttemptHistoryEntryDto.builder()
				.attemptId(row.getAttemptId())
				.practiceItemId(row.getPracticeItemId())
				.level(row.getLevel())
				.examType(row.getExamType())
				.topic(row.getTopic())
				.score(row.getScore())
				.attemptedAt(row.getCreatedAt())
				.build();
	}

	private GrammarPracticeItem requireItem(Long itemId) {
		GrammarPracticeItem item = grammarPracticeMapper.findItemById(itemId);
		if (item == null) {
			throw BusinessException.notFound("Grammar practice item not found: id=" + itemId);
		}
		return item;
	}

	private List<GrammarQuestionItem> readItems(String json) {
		try {
			return objectMapper.readValue(json, new TypeReference<List<GrammarQuestionItem>>() { });
		} catch (JsonProcessingException ex) {
			throw new IllegalStateException("Failed to deserialize grammar practice items", ex);
		}
	}

	private List<String> readWords(String json) {
		try {
			return objectMapper.readValue(json, new TypeReference<List<String>>() { });
		} catch (JsonProcessingException ex) {
			throw new IllegalStateException("Failed to deserialize grammar rule list", ex);
		}
	}

	private String writeJson(Object value) {
		try {
			return objectMapper.writeValueAsString(value);
		} catch (JsonProcessingException ex) {
			throw new IllegalStateException("Failed to serialize grammar practice content", ex);
		}
	}
}
