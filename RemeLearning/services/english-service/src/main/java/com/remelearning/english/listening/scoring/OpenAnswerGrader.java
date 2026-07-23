package com.remelearning.english.listening.scoring;

/**
 * Grades a free-text {@code OPEN} listening-comprehension answer against the passage/model
 * answer. Callers depend on this interface, not the implementation, so the grading provider can
 * change without touching them.
 */
public interface OpenAnswerGrader {

	/**
	 * Never throws - degrades to a neutral score with an explanatory feedback string on any
	 * LLM/parse failure, since a flaky call here can't block grading the rest of the attempt.
	 *
	 * @param passageTranscript the full listening passage, for context
	 * @param question           the question prompt
	 * @param modelAnswer        the reference answer used only for grading, never shown to the learner
	 * @param submittedAnswer    the learner's free-text response
	 */
	OpenAnswerGrade grade(String passageTranscript, String question, String modelAnswer, String submittedAnswer);
}
