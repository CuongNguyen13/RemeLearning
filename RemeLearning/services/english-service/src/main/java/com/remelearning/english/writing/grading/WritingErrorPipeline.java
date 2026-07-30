package com.remelearning.english.writing.grading;

import com.remelearning.common.constants.LearningCategories;
import com.remelearning.english.practice.dto.PracticeAttemptRequest;
import com.remelearning.english.practice.dto.PracticeRedoRequest;
import com.remelearning.english.practice.service.PracticeService;
import com.remelearning.english.writing.domain.WritingCriteriaScores;
import com.remelearning.english.writing.domain.WritingErrorItem;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

/**
 * What happens to a graded submission after the grader returns, shared by both writing tabs ("Học
 * thường" and "Thư viện") so the two cannot drift apart: score aggregation, routing labelled errors
 * into the learner's existing weak points, and turning them into Vietnamese next-steps.
 *
 * <p>Extracted as its own component rather than duplicated in each service because the weak-point
 * routing is the subtle part - it is what wires this skill into the review queue and
 * recommendations, and getting the item-id prefixes wrong silently forks a learner's history.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class WritingErrorPipeline {

	/**
	 * The {@code itemId} prefix each category's weak points are already keyed under. These are NOT
	 * simply the category names - vocabulary's existing rows use {@code "vocab:"} (see
	 * {@code VocabLearnServiceImpl}/{@code VocabularyLibraryServiceImpl}), so deriving a prefix from
	 * the category string would key writing mistakes under {@code "vocabulary:"} and quietly build a
	 * second, parallel set of rows instead of merging into the learner's real ones.
	 */
	private static final Map<String, String> ITEM_ID_PREFIXES = Map.of(
			LearningCategories.GRAMMAR, "grammar:",
			LearningCategories.VOCABULARY, "vocab:");

	private final PracticeService practiceService;

	/**
	 * Mean of the criteria that are actually populated for this task type. Computed here rather than
	 * taken from the LLM, which routinely reports an overall figure inconsistent with the very criteria
	 * it just scored.
	 */
	public double averageCriteria(WritingCriteriaScores criteria) {
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

	/**
	 * Turns each labelled error into one {@code PracticeAttemptRequest} and submits them as a single
	 * redo batch. That one call is what wires this skill into everything else: the dispatcher updates
	 * the owning domain's weak-point row, {@code mistake_history}'s Leitner schedule surfaces the label
	 * in the review queue, and the {@code learning.gap.analysis.requested} event it publishes lets
	 * recommendation-service/dashboard-service catch up.
	 *
	 * <p>Deduped by (category, label) so repeating the same mistake three times in one text counts as
	 * one weak point, not three. Errors whose category has no owning domain are skipped and logged.
	 * A submission with no routable errors makes no call at all.
	 */
	public void feedWeakPoints(String userId, List<WritingErrorItem> errors) {
		if (errors == null || errors.isEmpty()) {
			return;
		}
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

	/**
	 * Short Vietnamese next-steps, one per distinct error label - the same idea as listening's
	 * {@code actionAdvice}, giving the learner something to do beyond reading the corrections.
	 */
	public List<String> buildActionAdvice(List<WritingErrorItem> errors) {
		Set<String> advice = new LinkedHashSet<>();
		if (errors == null) {
			return new ArrayList<>(advice);
		}
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
}
