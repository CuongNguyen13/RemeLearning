package com.remelearning.english.writing.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.remelearning.common.constants.LearningCategories;
import com.remelearning.common.exception.BusinessException;
import com.remelearning.english.grammar.domain.GrammarWeakPoint;
import com.remelearning.english.grammar.service.GrammarWeakPointService;
import com.remelearning.english.practice.dto.PracticeAttemptRequest;
import com.remelearning.english.practice.dto.PracticeRedoRequest;
import com.remelearning.english.practice.service.PracticeService;
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
import com.remelearning.english.writing.grading.WritingGrade;
import com.remelearning.english.writing.grading.WritingGrader;
import com.remelearning.english.writing.mapper.WritingMapper;
import com.remelearning.english.writing.suggestion.NextSentenceSuggester;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Orchestrates the writing/translation skill, structurally mirroring
 * {@code ListeningLearnServiceImpl}: generate an AI prompt from the learner's weak points, grade the
 * submission, then feed each graded mistake back into the existing weak-point/spaced-repetition
 * pipeline via {@link PracticeService#redo}.
 *
 * <p>The one structural difference from every other skill: this domain has no weak-point table of
 * its own. Each error the grader reports already carries the category it belongs to, so errors are
 * routed into {@code grammar_weak_points}/{@code vocabulary_weak_points} instead - which is what
 * makes a "past perfect" slip while writing add to the same label already accumulated from
 * dictation/listening, rather than starting a parallel tally.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WritingLearnServiceImpl implements WritingLearnService {

	private static final int DEFAULT_FOCUS_LIMIT = 8;

	/**
	 * The {@code itemId} prefix each category's weak points are already keyed under. These are NOT
	 * simply the category names - vocabulary's existing rows use {@code "vocab:"} (see
	 * {@code VocabLearnServiceImpl}/{@code VocabularyLibraryServiceImpl}), so deriving the prefix
	 * from the category string would key writing mistakes under {@code "vocabulary:"} and quietly
	 * build a second, parallel set of rows instead of merging into the learner's real ones.
	 */
	private static final Map<String, String> ITEM_ID_PREFIXES = Map.of(
			LearningCategories.GRAMMAR, "grammar:",
			LearningCategories.VOCABULARY, "vocab:");

	private final WritingMapper writingMapper;
	private final WritingPracticeGenerator generator;
	private final WritingGrader grader;
	private final NextSentenceSuggester suggester;
	private final GrammarWeakPointService grammarWeakPointService;
	private final VocabularyWeakPointService vocabularyWeakPointService;
	private final PracticeService practiceService;
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

		double overallScore = averageCriteria(grade.criteria());
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

		feedWeakPoints(request.getUserId(), grade.errors());

		return WritingAttemptResultDto.builder()
				.attemptId(attempt.getId())
				.overallScore(overallScore)
				.criteria(grade.criteria())
				.correctedText(grade.correctedText())
				.errors(grade.errors())
				.feedback(grade.feedbackVi())
				.referenceAnswer(item.getReferenceAnswer())
				.actionAdvice(buildActionAdvice(grade.errors()))
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
	@Override
	@Transactional
	public List<WritingPracticeItemDto> generatePracticeFromAttempt(String userId, Long attemptId) {
		WritingAttemptDetailRow attempt = requireAttempt(userId, attemptId);
		List<String> mistakeLabels = WritingMistakeAnalyzer.extractMistakeLabels(attempt.getErrorsJson());
		return generatePracticeForLabels(
				userId, attempt.getTaskType(), mistakeLabels, attempt.getLevel(), attempt.getExamType());
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
		GeneratedWritingPractice generated = generator.generate(taskType, targetLabels, level, examType);

		WritingPracticeItem item = WritingPracticeItem.builder()
				.userId(userId)
				.taskType(taskType)
				.level(level)
				.examType(examType)
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

	// Turns each labelled error into one PracticeAttemptRequest and submits them as a single redo
	// batch. That one call is what wires this skill into everything else: the dispatcher updates the
	// owning domain's weak-point row, mistake_history's Leitner schedule surfaces the label in the
	// review queue, and the learning.gap.analysis.requested event it publishes lets
	// recommendation-service/dashboard-service catch up.
	//
	// Deduped by (category, label) so repeating the same mistake three times in one text counts as
	// one weak point, not three. An error with no routable prefix is skipped - the grader already
	// filters those out, this is a second guard for the persisted-history path.
	private void feedWeakPoints(String userId, List<WritingErrorItem> errors) {
		List<PracticeAttemptRequest> attempts = new ArrayList<>();
		Set<String> seenLabels = new LinkedHashSet<>();
		for (WritingErrorItem error : errors) {
			String prefix = ITEM_ID_PREFIXES.get(error.getCategory());
			if (prefix == null || error.getLabel() == null || error.getLabel().isBlank()) {
				log.warn("Skipping writing error with category '{}' - no weak-point domain to route it to",
						error.getCategory());
				continue;
			}
			String normalizedLabel = error.getLabel().trim().toLowerCase();
			if (!seenLabels.add(error.getCategory() + "|" + normalizedLabel)) {
				continue;
			}
			PracticeAttemptRequest attempt = new PracticeAttemptRequest();
			attempt.setItemId(prefix + normalizedLabel);
			attempt.setCategory(error.getCategory());
			attempt.setLabel(error.getLabel().trim());
			attempt.setCorrect(false);
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

	// Mean of the criteria that are actually populated for this task type. Computed here rather than
	// taken from the LLM, which routinely reports an overall figure inconsistent with the very
	// criteria it just scored.
	private double averageCriteria(WritingCriteriaScores criteria) {
		List<Double> scores = Stream.of(
						criteria.getGrammar(), criteria.getVocabulary(), criteria.getCoherence(),
						criteria.getAccuracy(), criteria.getTaskResponse())
				.filter(score -> score != null)
				.toList();
		if (scores.isEmpty()) {
			return 0.0;
		}
		return scores.stream().mapToDouble(Double::doubleValue).sum() / scores.size();
	}

	// Short Vietnamese next-steps, one per distinct error label, same idea as listening's
	// actionAdvice - gives the learner something to do beyond reading the corrections.
	private List<String> buildActionAdvice(List<WritingErrorItem> errors) {
		Set<String> advice = new LinkedHashSet<>();
		for (WritingErrorItem error : errors) {
			if (error.getLabel() == null || error.getLabel().isBlank()) {
				continue;
			}
			advice.add(LearningCategories.VOCABULARY.equals(error.getCategory())
					? "Ôn lại cách dùng \"%s\" và đặt thêm 3 câu với nó.".formatted(error.getLabel())
					: "Ôn lại quy tắc \"%s\" rồi viết lại 3 câu cho đúng.".formatted(error.getLabel()));
		}
		return new ArrayList<>(advice);
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
