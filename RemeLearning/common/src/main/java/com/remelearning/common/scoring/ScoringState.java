package com.remelearning.common.scoring;

import lombok.Builder;
import lombok.Getter;

/**
 * A learner's persisted per-item memory/mastery state, read before an attempt is graded and
 * written back (via the individual model updates) after. {@code daysSincePriorReview} is the
 * caller's responsibility to compute from its own timestamp column before invoking the engine -
 * this package has no notion of "now".
 */
@Getter
@Builder
public class ScoringState {

	/** Adaptive Ebbinghaus half-life (days) for this item - generalizes the fixed decay constant. */
	private double halfLifeDays;
	/** SM-2-style ease factor driving how fast the half-life grows/shrinks per attempt. */
	private double easeFactor;
	/** Bayesian Knowledge Tracing mastery probability, in [0,1]. */
	private double mastery;
	/** Current Leitner box index, 1-5. */
	private int leitnerBox;
	/** Days elapsed since this item was last reviewed, as of just before the current attempt. */
	private double daysSincePriorReview;
}
