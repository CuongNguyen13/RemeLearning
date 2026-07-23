package com.remelearning.english.speaking.generator;

import java.util.List;

/**
 * Generates one AI speaking-practice sentence/short passage naturally reusing a learner's target
 * words/sounds. Callers depend on this interface, not the implementation, so the generation
 * provider can change without touching them.
 */
public interface SpeakingPracticeGenerator {

	/**
	 * Never returns a result with a blank {@code targetText} and never throws - degrades to a
	 * static template on any LLM/parse failure.
	 *
	 * @param targetWords words/sounds to naturally reuse; may be empty, letting the implementation
	 *                     pick its own level-appropriate topic
	 * @param level        CEFR target (e.g. "B1"); null lets the implementation pick a default
	 * @param examType     exam style to frame the sentence around (e.g. "TOEIC"); may be null
	 */
	GeneratedSpeakingPractice generate(List<String> targetWords, String level, String examType);
}
