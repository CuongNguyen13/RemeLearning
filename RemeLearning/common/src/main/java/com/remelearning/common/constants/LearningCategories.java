package com.remelearning.common.constants;

/**
 * Canonical category strings carried on {@link com.remelearning.common.event.WeakPointPayload} and
 * used to route a weak point to its owning "Học &amp; Luyện tập với AI" skill page on the frontend
 * (vocabulary/grammar/pronunciation already existed as ai-service/dictation category strings;
 * "listening" is new, introduced alongside the listening-comprehension skill). New producers of
 * {@code learning.gap.analyzed} should use these constants instead of inlining the strings, so the
 * category never drifts out of sync across the vocabulary/grammar/listening/speaking packages.
 */
public final class LearningCategories {

	private LearningCategories() {
	}

	public static final String VOCABULARY = "vocabulary";
	public static final String GRAMMAR = "grammar";
	public static final String PRONUNCIATION = "pronunciation";
	public static final String LISTENING = "listening";
	/**
	 * The writing/translation skill. Unlike the others this is NOT used as a weak-point category:
	 * every mistake the writing grader reports is already labelled {@code grammar} or
	 * {@code vocabulary} and is routed there, so a writing slip merges with the same label from other
	 * skills. This constant marks the skill itself (recommendation exercise templates, UI routing).
	 */
	public static final String WRITING = "writing";
}
