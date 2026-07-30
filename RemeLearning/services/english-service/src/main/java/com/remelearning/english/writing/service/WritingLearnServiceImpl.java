package com.remelearning.english.writing.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.remelearning.common.constants.ExamTypes;
import com.remelearning.common.exception.BusinessException;
import com.remelearning.english.grammar.domain.GrammarWeakPoint;
import com.remelearning.english.grammar.service.GrammarWeakPointService;
import com.remelearning.english.vocabulary.domain.VocabularyWeakPoint;
import com.remelearning.english.vocabulary.service.VocabularyWeakPointService;
import com.remelearning.english.writing.domain.WritingAttempt;
import com.remelearning.english.writing.domain.WritingAttemptDetailRow;
import com.remelearning.english.writing.domain.WritingCriteriaScores;
import com.remelearning.english.writing.domain.WritingErrorItem;
import com.remelearning.english.writing.domain.WritingPracticeItem;
import com.remelearning.english.writing.domain.WritingSuggestion;
import com.remelearning.english.writing.domain.WritingTaskType;
import com.remelearning.english.writing.dto.GenerateWritingPracticeRequest;
import com.remelearning.english.writing.dto.SubmitWritingAttemptRequest;
import com.remelearning.english.writing.dto.SuggestNextSentenceRequest;
import com.remelearning.english.writing.dto.WritingAttemptDetailDto;
import com.remelearning.english.writing.dto.WritingAttemptHistoryEntryDto;
import com.remelearning.english.writing.dto.WritingAttemptResultDto;
import com.remelearning.english.writing.dto.WritingPracticeItemDto;
import com.remelearning.english.writing.generator.GeneratedWritingPractice;
import com.remelearning.english.writing.generator.WritingMistakeAnalyzer;
import com.remelearning.english.writing.generator.WritingPracticeGenerator;
import com.remelearning.english.writing.grading.WritingErrorPipeline;
import com.remelearning.english.writing.grading.WritingGrade;
import com.remelearning.english.writing.grading.WritingGrader;
import com.remelearning.english.writing.mapper.WritingMapper;
import com.remelearning.english.writing.suggestion.NextSentenceSuggester;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

/**
 * Orchestrates the writing/translation skill, structurally mirroring
 * {@code ListeningLearnServiceImpl}: generate an AI prompt from the learner's weak points, grade the
 * submission, then feed each graded mistake back into the existing weak-point/spaced-repetition
 * pipeline via {@code PracticeService#redo}.
 *
 * <p>The one structural difference from every other skill: this domain has no weak-point table of
 * its own. Each error the grader reports already carries the category it belongs to, so errors are
 * routed into {@code grammar_weak_points}/{@code vocabulary_weak_points} instead - which is what
 * makes a "past perfect" slip while writing add to the same label already accumulated from
 * dictation/listening, rather than starting a parallel tally. That routing lives in
 * {@link WritingErrorPipeline}, shared with the library tab.
 */
@Service
@RequiredArgsConstructor
public class WritingLearnServiceImpl implements WritingLearnService {

	private static final int DEFAULT_FOCUS_LIMIT = 8;

	private final WritingMapper writingMapper;
	private final WritingPracticeGenerator generator;
	private final WritingGrader grader;
	private final NextSentenceSuggester suggester;
	private final WritingErrorPipeline errorPipeline;
	private final GrammarWeakPointService grammarWeakPointService;
	private final VocabularyWeakPointService vocabularyWeakPointService;
	private final ObjectMapper objectMapper;

	@Override
	@Transactional
	public WritingPracticeItemDto generate(String userId, GenerateWritingPracticeRequest request) {
		List<String> targetLabels = resolveTargetLabels(userId, request.getFocusItems());
		return generateAndPersist(userId, request.getTaskType(), targetLabels, request.getLevel(), request.getExamType());
	}

	@Override
	public WritingPracticeItemDto getItem(Long itemId) {
		return toItemDto(requireItem(itemId));
	}

	@Override
	public List<WritingPracticeItemDto> listItems(String userId) {
		return writingMapper.findItemsByUserId(userId).stream().map(this::toItemDto).toList();
	}

	// Passes only the prompt text to the suggester - never the reference answer, which for a
	// translation task would turn a hint into the answer (see NextSentenceSuggester's contract).
	@Override
	public List<WritingSuggestion> suggest(SuggestNextSentenceRequest request) {
		WritingPracticeItem item = requireItem(request.getPracticeItemId());
		return suggester.suggest(item.getTaskType(), item.getPromptText(), request.getDraftText(), item.getLevel());
	}

	// Grades the submission, persists it with the grader's criteria/errors as-is (grading is
	// LLM-backed, so it must never be re-run just to view history), then turns the labelled errors
	// into weak-point updates. The reference answer is only revealed in the returned result.
	@Override
	@Transactional
	public WritingAttemptResultDto submit(SubmitWritingAttemptRequest request) {
		WritingPracticeItem item = requireItem(request.getPracticeItemId());
		WritingGrade grade = grader.grade(
				item.getTaskType(), item.getPromptText(), item.getReferenceAnswer(), request.getSubmittedText());

		double overallScore = errorPipeline.averageCriteria(grade.criteria());
		WritingAttempt attempt = WritingAttempt.builder()
				.practiceItemId(item.getId())
				.userId(request.getUserId())
				.submittedText(request.getSubmittedText())
				.correctedText(grade.correctedText())
				.overallScore(overallScore)
				.criteriaJson(writeJson(grade.criteria()))
				.errorsJson(writeJson(grade.errors()))
				.feedback(grade.feedbackVi())
				.build();
		writingMapper.insertAttempt(attempt);

		errorPipeline.feedWeakPoints(request.getUserId(), grade.errors());

		return WritingAttemptResultDto.builder()
				.attemptId(attempt.getId())
				.overallScore(overallScore)
				.criteria(grade.criteria())
				.correctedText(grade.correctedText())
				.errors(grade.errors())
				.feedback(grade.feedbackVi())
				.referenceAnswer(item.getReferenceAnswer())
				.actionAdvice(errorPipeline.buildActionAdvice(grade.errors()))
				.build();
	}

	@Override
	public List<WritingAttemptHistoryEntryDto> getHistory(String userId) {
		return writingMapper.findHistoryByUserId(userId).stream()
				.map(row -> WritingAttemptHistoryEntryDto.builder()
						.attemptId(row.getAttemptId())
						.practiceItemId(row.getPracticeItemId())
						.taskType(row.getTaskType())
						.level(row.getLevel())
						.examType(row.getExamType())
						.topic(row.getTopic())
						.score(row.getScore())
						.attemptedAt(row.getCreatedAt())
						.build())
				.toList();
	}

	@Override
	public WritingAttemptDetailDto getAttemptDetail(String userId, Long attemptId) {
		WritingAttemptDetailRow row = requireAttempt(userId, attemptId);
		return WritingAttemptDetailDto.builder()
				.attemptId(row.getAttemptId())
				.practiceItemId(row.getPracticeItemId())
				.taskType(row.getTaskType())
				.level(row.getLevel())
				.examType(row.getExamType())
				.topic(row.getTopic())
				.promptText(row.getPromptText())
				.submittedText(row.getSubmittedText())
				.correctedText(row.getCorrectedText())
				.overallScore(row.getOverallScore())
				.criteria(readCriteria(row.getCriteriaJson()))
				.errors(readErrors(row.getErrorsJson()))
				.feedback(row.getFeedback())
				.referenceAnswer(row.getReferenceAnswer())
				.attemptedAt(row.getCreatedAt())
				.build();
	}

	// Generates practice aimed at one past attempt's mistakes: verifies the attempt belongs to this
	// learner, pulls its error labels via the pure WritingMistakeAnalyzer, then reuses the exact same
	// generate-and-persist pipeline generate() uses so the new prompt lands in the same bank as a
	// normal "học thường" one.
	//
	// A caller-supplied examType overrides the original attempt's, letting the learner re-target the
	// same mistakes at a different exam ("I got these wrong on a General task - give me them in IELTS
	// register"); omitting it keeps the original, which is the common case.
	@Override
	@Transactional
	public List<WritingPracticeItemDto> generatePracticeFromAttempt(String userId, Long attemptId, String examType) {
		WritingAttemptDetailRow attempt = requireAttempt(userId, attemptId);
		List<String> mistakeLabels = WritingMistakeAnalyzer.extractMistakeLabels(attempt.getErrorsJson());
		String resolvedExamType = ExamTypes.normalize(examType) == null
				? attempt.getExamType() : ExamTypes.normalize(examType);
		return generatePracticeForLabels(
				userId, attempt.getTaskType(), mistakeLabels, attempt.getLevel(), resolvedExamType);
	}

	@Override
	@Transactional
	public List<WritingPracticeItemDto> generatePracticeForLabels(
			String userId, WritingTaskType taskType, List<String> targetLabels, String level, String examType) {
		generateAndPersist(userId, taskType, targetLabels, level, examType);
		return listItems(userId);
	}

	// --- helpers ---

	// Actual generation+persistence work shared by generate() and generatePracticeForLabels():
	// calls the AI generator and inserts the resulting prompt.
	private WritingPracticeItemDto generateAndPersist(
			String userId, WritingTaskType taskType, List<String> targetLabels, String level, String examType) {
		// Normalized once here so "toeic"/"Toeic"/"TOEIC" all persist as one value - the retry action
		// and the exam-profile lookup both key off this column.
		String normalizedExamType = ExamTypes.normalize(examType);
		GeneratedWritingPractice generated = generator.generate(taskType, targetLabels, level, normalizedExamType);

		WritingPracticeItem item = WritingPracticeItem.builder()
				.userId(userId)
				.taskType(taskType)
				.level(level)
				.examType(normalizedExamType)
				.topic(generated.topic())
				.promptText(generated.promptText())
				.sourceLang(taskType.sourceLang())
				.targetLang(taskType.targetLang())
				.referenceAnswer(generated.referenceAnswer())
				.targetLabelsJson(writeJson(targetLabels))
				.build();
		writingMapper.insertItem(item);

		return toItemDto(item);
	}

	// Explicit focusItems win; otherwise targets the learner's most-forgotten grammar AND vocabulary
	// labels together - writing is the one skill that exercises both at once, so both domains feed
	// the prompt. An empty result lets the generator pick its own topic (brand-new learner).
	private List<String> resolveTargetLabels(String userId, List<String> focusItems) {
		if (focusItems != null && !focusItems.isEmpty()) {
			return focusItems;
		}
		Stream<String> grammarLabels = grammarWeakPointService.getTopWeakPoints(userId, DEFAULT_FOCUS_LIMIT).stream()
				.map(GrammarWeakPoint::getLabel);
		Stream<String> vocabularyLabels = vocabularyWeakPointService.getTopWeakPoints(userId, DEFAULT_FOCUS_LIMIT).stream()
				.map(VocabularyWeakPoint::getLabel);
		return Stream.concat(grammarLabels, vocabularyLabels)
				.filter(label -> label != null && !label.isBlank())
				.distinct()
				.limit(DEFAULT_FOCUS_LIMIT)
				.toList();
	}

	private WritingPracticeItemDto toItemDto(WritingPracticeItem item) {
		return WritingPracticeItemDto.builder()
				.practiceItemId(item.getId())
				.taskType(item.getTaskType())
				.level(item.getLevel())
				.examType(item.getExamType())
				.topic(item.getTopic())
				.promptText(item.getPromptText())
				.sourceLang(item.getSourceLang())
				.targetLang(item.getTargetLang())
				.targetLabels(readLabels(item.getTargetLabelsJson()))
				.createdAt(item.getCreatedAt())
				.build();
	}

	private WritingPracticeItem requireItem(Long itemId) {
		WritingPracticeItem item = writingMapper.findItemById(itemId);
		if (item == null) {
			throw BusinessException.notFound("Writing practice item not found: id=" + itemId);
		}
		return item;
	}

	private WritingAttemptDetailRow requireAttempt(String userId, Long attemptId) {
		WritingAttemptDetailRow row = writingMapper.findAttemptDetailByIdAndUserId(attemptId, userId);
		if (row == null) {
			throw BusinessException.notFound("Writing practice attempt not found: id=" + attemptId);
		}
		return row;
	}

	private List<String> readLabels(String json) {
		if (json == null || json.isBlank()) {
			return List.of();
		}
		try {
			return objectMapper.readValue(json, new TypeReference<List<String>>() { });
		} catch (JsonProcessingException ex) {
			throw new IllegalStateException("Failed to deserialize writing target labels", ex);
		}
	}

	private List<WritingErrorItem> readErrors(String json) {
		if (json == null || json.isBlank()) {
			return List.of();
		}
		try {
			return objectMapper.readValue(json, new TypeReference<List<WritingErrorItem>>() { });
		} catch (JsonProcessingException ex) {
			throw new IllegalStateException("Failed to deserialize writing attempt errors", ex);
		}
	}

	private WritingCriteriaScores readCriteria(String json) {
		if (json == null || json.isBlank()) {
			return WritingCriteriaScores.builder().build();
		}
		try {
			return objectMapper.readValue(json, WritingCriteriaScores.class);
		} catch (JsonProcessingException ex) {
			throw new IllegalStateException("Failed to deserialize writing attempt criteria", ex);
		}
	}

	private String writeJson(Object value) {
		try {
			return objectMapper.writeValueAsString(value);
		} catch (JsonProcessingException ex) {
			throw new IllegalStateException("Failed to serialize writing practice content", ex);
		}
	}
}
