package com.remelearning.english.vocabulary.learn.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.remelearning.common.constants.LearningCategories;
import com.remelearning.common.exception.BusinessException;
import com.remelearning.english.practice.dto.PracticeAttemptRequest;
import com.remelearning.english.practice.dto.PracticeRedoRequest;
import com.remelearning.english.practice.service.PracticeService;
import com.remelearning.english.vocabulary.domain.VocabularyWeakPoint;
import com.remelearning.english.vocabulary.learn.domain.VocabAttemptDetailRow;
import com.remelearning.english.vocabulary.learn.domain.VocabAttemptHistoryRow;
import com.remelearning.english.vocabulary.learn.domain.VocabPracticeAttempt;
import com.remelearning.english.vocabulary.learn.domain.VocabPracticeItem;
import com.remelearning.english.vocabulary.learn.domain.VocabQuestionItem;
import com.remelearning.english.vocabulary.learn.dto.GenerateVocabPracticeRequest;
import com.remelearning.english.vocabulary.learn.dto.SubmitVocabAttemptRequest;
import com.remelearning.english.vocabulary.learn.dto.VocabAttemptDetailDto;
import com.remelearning.english.vocabulary.learn.dto.VocabAttemptHistoryEntryDto;
import com.remelearning.english.vocabulary.learn.dto.VocabAttemptQuestionResultDto;
import com.remelearning.english.vocabulary.learn.dto.VocabAttemptResultDto;
import com.remelearning.english.vocabulary.learn.dto.VocabPracticeItemDto;
import com.remelearning.english.vocabulary.learn.dto.VocabQuestionDto;
import com.remelearning.english.vocabulary.learn.generator.GeneratedVocabPractice;
import com.remelearning.english.vocabulary.learn.generator.VocabPracticeGenerator;
import com.remelearning.english.vocabulary.learn.mapper.VocabPracticeMapper;
import com.remelearning.english.vocabulary.learn.scoring.VocabAttemptScorer;
import com.remelearning.english.vocabulary.learn.scoring.VocabScoreResult;
import com.remelearning.english.vocabulary.service.VocabularyWeakPointService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Orchestrates the vocabulary "learn" skill: generating an AI practice set (targeting the
 * learner's own weak points when no explicit focus is given), grading a submitted attempt with
 * the pure {@link VocabAttemptScorer}, and feeding each graded word back into the existing
 * spaced-repetition/weak-point pipeline via {@link PracticeService#redo} - reusing
 * {@code WeakPointScoringOrchestrator}/{@code mistake_history}/{@code AnalysisRequestedProducer}
 * exactly as the practice/redo flow does, instead of a bespoke publisher for this skill.
 */
@Service
@RequiredArgsConstructor
public class VocabLearnServiceImpl implements VocabLearnService {

	private static final int DEFAULT_FOCUS_LIMIT = 8;
	private static final String ITEM_ID_PREFIX = "vocab:";

	private final VocabPracticeMapper vocabPracticeMapper;
	private final VocabPracticeGenerator generator;
	private final VocabularyWeakPointService vocabularyWeakPointService;
	private final PracticeService practiceService;
	private final ObjectMapper objectMapper;

	@Override
	@Transactional
	public VocabPracticeItemDto generate(String userId, GenerateVocabPracticeRequest request) {
		List<String> targetWords = resolveTargetWords(userId, request.getFocusItems());
		GeneratedVocabPractice generated = generator.generate(targetWords, request.getLevel(), request.getExamType());

		VocabPracticeItem item = VocabPracticeItem.builder()
				.userId(userId)
				.level(request.getLevel())
				.examType(request.getExamType())
				.topic(generated.topic())
				.targetWordsJson(writeJson(targetWords))
				.itemsJson(writeJson(generated.items()))
				.build();
		vocabPracticeMapper.insertItem(item);

		return toItemDto(item, targetWords, generated.items());
	}

	@Override
	public VocabPracticeItemDto getItem(Long itemId) {
		VocabPracticeItem item = requireItem(itemId);
		return toItemDto(item, readWords(item.getTargetWordsJson()), readItems(item.getItemsJson()));
	}

	@Override
	public List<VocabPracticeItemDto> listItems(String userId) {
		return vocabPracticeMapper.findItemsByUserId(userId).stream()
				.map(item -> toItemDto(item, readWords(item.getTargetWordsJson()), readItems(item.getItemsJson())))
				.toList();
	}

	@Override
	@Transactional
	public VocabAttemptResultDto submit(SubmitVocabAttemptRequest request) {
		VocabPracticeItem item = requireItem(request.getPracticeItemId());
		List<VocabQuestionItem> questions = readItems(item.getItemsJson());

		VocabScoreResult score = VocabAttemptScorer.score(questions, request.getAnswers());

		VocabPracticeAttempt attempt = VocabPracticeAttempt.builder()
				.practiceItemId(item.getId())
				.userId(request.getUserId())
				.answersJson(writeJson(request.getAnswers()))
				.score(score.getAccuracy())
				.build();
		vocabPracticeMapper.insertAttempt(attempt);

		List<VocabAttemptQuestionResultDto> results = buildQuestionResults(questions, request.getAnswers(), score);
		feedWeakPoints(request.getUserId(), questions, score);

		return VocabAttemptResultDto.builder()
				.accuracy(score.getAccuracy())
				.results(results)
				.actionAdvice(buildActionAdvice(questions, score))
				.build();
	}

	@Override
	public List<VocabAttemptHistoryEntryDto> getHistory(String userId) {
		return vocabPracticeMapper.findHistoryByUserId(userId).stream()
				.map(this::toHistoryEntryDto)
				.toList();
	}

	@Override
	public VocabAttemptDetailDto getAttemptDetail(String userId, Long attemptId) {
		VocabAttemptDetailRow row = vocabPracticeMapper.findAttemptDetailByIdAndUserId(attemptId, userId);
		if (row == null) {
			throw BusinessException.notFound("Vocabulary practice attempt not found: id=" + attemptId);
		}
		List<VocabQuestionItem> questions = readItems(row.getItemsJson());
		List<String> answers = readWords(row.getAnswersJson());
		VocabScoreResult score = VocabAttemptScorer.score(questions, answers);

		return VocabAttemptDetailDto.builder()
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

	// Explicit focusItems (from a "Luyện ngay" deep-link) win; otherwise falls back to the
	// learner's own top vocabulary weak points; an empty result lets the generator pick its own
	// level-appropriate words (brand-new learner with no history yet).
	private List<String> resolveTargetWords(String userId, List<String> focusItems) {
		if (focusItems != null && !focusItems.isEmpty()) {
			return focusItems;
		}
		return vocabularyWeakPointService.getTopWeakPoints(userId, DEFAULT_FOCUS_LIMIT).stream()
				.map(VocabularyWeakPoint::getLabel)
				.toList();
	}

	// Scores each question directly against the existing practice/redo pipeline: mistake_history +
	// the Java scoring engine update the owning vocabulary_weak_points row immediately, and the
	// bundled learning.gap.analysis.requested re-analysis keeps recommendation-service/
	// dashboard-service in sync - the same mechanism a learner's spaced-repetition redo uses.
	private void feedWeakPoints(String userId, List<VocabQuestionItem> questions, VocabScoreResult score) {
		List<PracticeAttemptRequest> attempts = new ArrayList<>();
		Set<String> seenWords = new LinkedHashSet<>();
		for (int i = 0; i < questions.size(); i++) {
			VocabQuestionItem question = questions.get(i);
			if (!seenWords.add(question.getTargetWord().toLowerCase())) {
				continue;
			}
			PracticeAttemptRequest attempt = new PracticeAttemptRequest();
			attempt.setItemId(ITEM_ID_PREFIX + question.getTargetWord().toLowerCase());
			attempt.setCategory(LearningCategories.VOCABULARY);
			attempt.setLabel(question.getTargetWord());
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

	private List<String> buildActionAdvice(List<VocabQuestionItem> questions, VocabScoreResult score) {
		Set<String> advice = new LinkedHashSet<>();
		for (int i = 0; i < questions.size(); i++) {
			if (Boolean.TRUE.equals(score.getPerQuestionCorrect().get(i))) {
				continue;
			}
			VocabQuestionItem question = questions.get(i);
			String translation = question.getTranslation();
			advice.add("Ôn lại từ '%s'%s. Đặt câu mới với từ này để ghi nhớ lâu hơn."
					.formatted(question.getTargetWord(), translation == null ? "" : " (nghĩa: " + translation + ")"));
		}
		return new ArrayList<>(advice);
	}

	private List<VocabAttemptQuestionResultDto> buildQuestionResults(
			List<VocabQuestionItem> questions, List<String> answers, VocabScoreResult score) {
		List<VocabAttemptQuestionResultDto> results = new ArrayList<>();
		for (int i = 0; i < questions.size(); i++) {
			VocabQuestionItem question = questions.get(i);
			results.add(VocabAttemptQuestionResultDto.builder()
					.index(i)
					.prompt(question.getPrompt())
					.yourAnswer(i < answers.size() ? answers.get(i) : null)
					.correctAnswer(question.getAnswer())
					.correct(score.getPerQuestionCorrect().get(i))
					.translation(question.getTranslation())
					.build());
		}
		return results;
	}

	private VocabPracticeItemDto toItemDto(VocabPracticeItem item, List<String> targetWords, List<VocabQuestionItem> questions) {
		List<VocabQuestionDto> questionDtos = new ArrayList<>();
		for (int i = 0; i < questions.size(); i++) {
			VocabQuestionItem question = questions.get(i);
			questionDtos.add(VocabQuestionDto.builder()
					.index(i)
					.prompt(question.getPrompt())
					.type(question.getType())
					.options(question.getOptions())
					.answer(question.getAnswer())
					.translation(question.getTranslation())
					.build());
		}
		return VocabPracticeItemDto.builder()
				.practiceItemId(item.getId())
				.level(item.getLevel())
				.examType(item.getExamType())
				.topic(item.getTopic())
				.targetWords(targetWords)
				.questions(questionDtos)
				.createdAt(item.getCreatedAt())
				.build();
	}

	private VocabAttemptHistoryEntryDto toHistoryEntryDto(VocabAttemptHistoryRow row) {
		return VocabAttemptHistoryEntryDto.builder()
				.attemptId(row.getAttemptId())
				.practiceItemId(row.getPracticeItemId())
				.level(row.getLevel())
				.examType(row.getExamType())
				.topic(row.getTopic())
				.score(row.getScore())
				.attemptedAt(row.getCreatedAt())
				.build();
	}

	private VocabPracticeItem requireItem(Long itemId) {
		VocabPracticeItem item = vocabPracticeMapper.findItemById(itemId);
		if (item == null) {
			throw BusinessException.notFound("Vocabulary practice item not found: id=" + itemId);
		}
		return item;
	}

	private List<VocabQuestionItem> readItems(String json) {
		try {
			return objectMapper.readValue(json, new TypeReference<List<VocabQuestionItem>>() { });
		} catch (JsonProcessingException ex) {
			throw new IllegalStateException("Failed to deserialize vocab practice items", ex);
		}
	}

	private List<String> readWords(String json) {
		try {
			return objectMapper.readValue(json, new TypeReference<List<String>>() { });
		} catch (JsonProcessingException ex) {
			throw new IllegalStateException("Failed to deserialize vocab word list", ex);
		}
	}

	private String writeJson(Object value) {
		try {
			return objectMapper.writeValueAsString(value);
		} catch (JsonProcessingException ex) {
			throw new IllegalStateException("Failed to serialize vocab practice content", ex);
		}
	}
}
