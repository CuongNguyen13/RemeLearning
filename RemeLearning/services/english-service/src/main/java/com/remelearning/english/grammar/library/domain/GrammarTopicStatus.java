package com.remelearning.english.grammar.library.domain;

/**
 * Progression state of one learner against one {@link GrammarLibraryTopic}: {@code LOCKED} (not
 * reachable yet), {@code UNLOCKED} (reachable, no session started), {@code IN_PROGRESS} (at least
 * one session started but not yet passed), {@code PASSED} (all questions in some session were
 * answered correctly, including after retries).
 */
public enum GrammarTopicStatus {
	LOCKED,
	UNLOCKED,
	IN_PROGRESS,
	PASSED
}
