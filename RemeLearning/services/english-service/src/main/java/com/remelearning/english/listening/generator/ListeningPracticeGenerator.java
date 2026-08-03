package com.remelearning.english.listening.generator;

import java.util.List;

/**
 * Generates a whole practice session's worth of AI listening-comprehension passages (each a
 * monologue or dialogue's lines, ready for {@code DialogueAudioSynthesizer}, plus its MCQ/keyword/
 * open questions), reusing a learner's target keywords when given. Callers depend on this
 * interface, not the implementation, so the generation provider can change without touching them.
 */
public interface ListeningPracticeGenerator {

	/**
	 * Never returns an empty list nor a passage with a null/empty lines or questions list, and never
	 * throws - degrades to a single static template passage on any LLM/parse failure. May return
	 * fewer passages than {@link ListeningSessionRequest#passageCount()} when the model
	 * under-delivers or part of what it returned was unusable.
	 */
	List<GeneratedListeningPractice> generate(ListeningSessionRequest request);
}
