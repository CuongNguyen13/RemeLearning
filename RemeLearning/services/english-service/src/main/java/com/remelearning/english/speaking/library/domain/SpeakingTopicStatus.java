package com.remelearning.english.speaking.library.domain;

/**
 * Progression state of one learner against one {@link SpeakingLibraryTopic}: {@code LOCKED} (not
 * reachable yet), {@code UNLOCKED} (reachable, no section started), {@code IN_PROGRESS} (a section
 * started but not yet passed), {@code PASSED} (every sentence in a section scored above threshold).
 */
public enum SpeakingTopicStatus {
	LOCKED,
	UNLOCKED,
	IN_PROGRESS,
	PASSED
}
