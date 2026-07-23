package com.remelearning.english.vocabulary.learn.generator;

import java.util.List;

/**
 * Generates one AI vocabulary practice set (cloze/MCQ/matching-by-meaning questions), reusing a
 * learner's target words when given. Callers depend on this interface, not the implementation, so
 * the generation provider can change without touching them.
 */
public interface VocabPracticeGenerator {

	/**
	 * Never returns a result with a null/empty item list and never throws - degrades to a static
	 * template set on any LLM/parse failure.
	 *
	 * @param targetWords the words to drill; may be empty, letting the implementation pick its own
	 *                     level-appropriate words
	 * @param level       CEFR target (e.g. "B1"); null lets the implementation pick a sensible default
	 * @param examType    exam style to frame the passage around (e.g. "TOEIC"); may be null
	 */
	GeneratedVocabPractice generate(List<String> targetWords, String level, String examType);
}
