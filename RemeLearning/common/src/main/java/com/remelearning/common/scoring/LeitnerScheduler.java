package com.remelearning.common.scoring;

import java.time.Duration;
import java.time.Instant;

/**
 * Classic 5-box Leitner spaced-repetition scheduler. This answers a different question than
 * {@link WeakPointScoringEngine}'s weak_score: not "how weak is this item right now" but "when
 * should the learner see it again" - a correct answer promotes the item to a further-out box
 * (longer interval), an incorrect answer demotes it straight back to box 1 (review tomorrow).
 */
public final class LeitnerScheduler {

	private static final int MIN_BOX = 1;
	private static final int MAX_BOX = 5;
	/** Review interval in days for each box, indexed by (box - 1). */
	private static final int[] INTERVAL_DAYS_BY_BOX = {1, 2, 4, 7, 14};

	private LeitnerScheduler() {
	}

	/** Advances the box on a correct answer (capped at 5), resets to box 1 on an incorrect one, and derives the next review instant from the resulting box's interval. */
	public static LeitnerResult next(int currentBox, boolean correct, Instant now) {
		int box = correct
				? Math.min(MAX_BOX, currentBox + 1)
				: MIN_BOX;
		int intervalDays = INTERVAL_DAYS_BY_BOX[box - 1];
		return LeitnerResult.builder()
				.box(box)
				.nextReviewAt(now.plus(Duration.ofDays(intervalDays)))
				.build();
	}
}
