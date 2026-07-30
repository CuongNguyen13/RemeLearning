package com.remelearning.english.practice.session.service;

import com.remelearning.english.practice.session.dto.PracticeSessionDto;

/**
 * Orchestrates a "practice session": a bundle of ~4 AI-generated exercises mixed across the four
 * skills (vocabulary/grammar/listening/speaking), each aimed at the learner's highest-scoring weak
 * points. This layer only picks categories + triggers each domain's existing {@code generate} and
 * tracks progress; it never re-implements exercise generation or scoring - the domains own those.
 */
public interface PracticeSessionService {

	/** Generates and persists a new session (one AI exercise per slot), returning it for the client to run. */
	PracticeSessionDto startSession(String userId, Integer exerciseCount);

	/** Reads back a session with its exercise slots. Not-found if the id is unknown. */
	PracticeSessionDto getSession(Long sessionId);

	/** The learner's most recent still-in-progress session (for resume), or null if none. */
	PracticeSessionDto getLatestInProgress(String userId);

	/**
	 * Records the score for one exercise slot (after the learner submitted it through its domain) and,
	 * once every slot is done, marks the whole session completed. Returns the refreshed session.
	 */
	PracticeSessionDto completeExercise(Long sessionId, int exerciseOrder, Double score);
}
