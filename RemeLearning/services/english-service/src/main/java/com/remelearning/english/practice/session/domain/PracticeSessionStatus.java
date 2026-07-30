package com.remelearning.english.practice.session.domain;

/** Lifecycle of a practice session: created with slots to do, then completed once every slot is DONE. */
public enum PracticeSessionStatus {
	IN_PROGRESS,
	COMPLETED
}
