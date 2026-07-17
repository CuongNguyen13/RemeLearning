package com.remelearning.common.scoring;

import lombok.Builder;
import lombok.Getter;

import java.time.Instant;

/** Output of {@link WeakPointScoringEngine#scoreAfterAttempt}: the score plus the new state to persist. */
@Getter
@Builder
public class ScoringResult {

	/** The composite weak-point score for this item, right now, ready to persist/rank by. */
	private double weakScore;
	/** Updated memory/mastery state to persist for the next attempt. */
	private ScoringState updatedState;
	/** When this item is next due for review, per the Leitner schedule. */
	private Instant nextReviewAt;
}
