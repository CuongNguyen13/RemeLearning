package com.remelearning.english.writing.library.domain;

/**
 * Gating state of one writing-library topic for one learner - the same state machine
 * {@code ListeningTopicStatus}/{@code GrammarTopicStatus} use, so the UI treats every library
 * identically. A learner with no row yet counts as {@link #LOCKED}.
 */
public enum WritingTopicStatus {
	LOCKED,
	UNLOCKED,
	IN_PROGRESS,
	PASSED
}
