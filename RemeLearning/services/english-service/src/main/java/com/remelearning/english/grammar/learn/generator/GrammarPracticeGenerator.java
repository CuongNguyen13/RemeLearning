package com.remelearning.english.grammar.learn.generator;

import java.util.List;

/**
 * Generates one AI grammar practice set (error-correction/fill-tense/transform/MCQ questions),
 * reusing a learner's target rules when given. Callers depend on this interface, not the
 * implementation, so the generation provider can change without touching them.
 */
public interface GrammarPracticeGenerator {

	/**
	 * Never returns a result with a null/empty item list and never throws - degrades to a static
	 * template set on any LLM/parse failure.
	 *
	 * @param targetRules the grammar rules to drill; may be empty, letting the implementation pick
	 *                     its own level-appropriate rules
	 * @param level       CEFR target (e.g. "B1"); null lets the implementation pick a sensible default
	 * @param examType    exam style to frame the passage around (e.g. "TOEIC"); may be null
	 */
	GeneratedGrammarPractice generate(List<String> targetRules, String level, String examType);
}
