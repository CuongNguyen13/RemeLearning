package com.remelearning.english.listening.generator;

import java.util.List;

/**
 * Generates one AI listening-comprehension passage (monologue or dialogue lines, ready for
 * {@code DialogueAudioSynthesizer}) plus its MCQ/keyword/open questions, reusing a learner's
 * target keywords when given. Callers depend on this interface, not the implementation, so the
 * generation provider can change without touching them.
 */
public interface ListeningPracticeGenerator {

	/**
	 * Never returns a result with a null/empty lines or questions list and never throws - degrades
	 * to a static template on any LLM/parse failure.
	 *
	 * @param targetKeywords words/phrases to naturally reuse in the passage; may be empty, letting
	 *                        the implementation pick its own level-appropriate topic
	 * @param level           CEFR target (e.g. "B1"); null lets the implementation pick a default
	 * @param examType        exam style to frame the passage around (e.g. "TOEIC"); may be null
	 * @param translationLang UI language to also translate each line into; null/"en" means none
	 */
	GeneratedListeningPractice generate(List<String> targetKeywords, String level, String examType, String translationLang);
}
