package com.remelearning.english.practice.session.domain;

/** Lifecycle of one exercise slot in a session: PENDING until the learner submits it, then DONE. */
public enum PracticeExerciseStatus {
	PENDING,
	DONE
}
