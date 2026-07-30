package com.remelearning.english.writing.grading;

import com.remelearning.english.writing.domain.WritingCriteriaScores;
import com.remelearning.english.writing.domain.WritingErrorItem;

import java.util.List;

/**
 * The grader's verdict on one submission. Note there is no overall score here on purpose: the
 * caller computes it as the mean of the populated criteria, rather than trusting a figure the LLM
 * invents alongside (and often inconsistently with) the criteria it just scored.
 *
 * @param criteria      per-criterion scores, each already clamped to [0, 1]
 * @param correctedText the submission rewritten correctly
 * @param errors        labelled mistakes; the {@code label}/{@code category} pairs are what feed the
 *                      weak-point pipeline
 * @param feedbackVi    overall remarks in Vietnamese
 */
public record WritingGrade(
		WritingCriteriaScores criteria,
		String correctedText,
		List<WritingErrorItem> errors,
		String feedbackVi) {
}
