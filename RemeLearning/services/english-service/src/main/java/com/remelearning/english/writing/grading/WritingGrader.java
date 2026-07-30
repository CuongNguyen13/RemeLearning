package com.remelearning.english.writing.grading;

import com.remelearning.english.writing.domain.WritingTaskType;

/**
 * Grades a learner's writing or translation against the prompt and reference answer. Callers depend
 * on this interface, not the implementation, so the grading provider can change without touching
 * them.
 */
public interface WritingGrader {

	/**
	 * Never throws - degrades to a neutral score with an explanatory Vietnamese feedback string and
	 * no errors on any LLM/parse failure, since a flaky call here must not lose the learner's work.
	 *
	 * @param taskType        decides which fourth criterion is scored (accuracy vs taskResponse)
	 * @param promptText      the brief or source passage the learner answered
	 * @param referenceAnswer model answer / reference translation; may be null
	 * @param submittedText   the learner's text
	 */
	WritingGrade grade(WritingTaskType taskType, String promptText, String referenceAnswer, String submittedText);
}
