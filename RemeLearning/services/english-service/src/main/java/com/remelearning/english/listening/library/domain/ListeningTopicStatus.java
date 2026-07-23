package com.remelearning.english.listening.library.domain;

/**
 * Progression state of one learner against one {@link ListeningLibraryTopic}: {@code LOCKED} (not
 * reachable yet), {@code UNLOCKED} (reachable, no section started), {@code IN_PROGRESS} (a section
 * started but not yet passed), {@code PASSED} (a section's score met the pass threshold).
 */
public enum ListeningTopicStatus {
	LOCKED,
	UNLOCKED,
	IN_PROGRESS,
	PASSED
}
