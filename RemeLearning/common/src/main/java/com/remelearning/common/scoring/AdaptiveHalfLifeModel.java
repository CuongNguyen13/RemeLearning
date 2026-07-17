package com.remelearning.common.scoring;

/**
 * Generalizes the classic Ebbinghaus forgetting curve (fixed 7-day decay constant) into a
 * per-item, per-learner half-life that adapts from attempt history, SM-2-style: correct answers
 * stretch the half-life out (and grow the ease factor that drives future stretching), incorrect
 * answers collapse it back down. This is a pragmatic, no-training-data stand-in for a fully
 * trained Half-Life Regression model (Duolingo's approach) - this repo has neither the review-log
 * volume nor the offline training infrastructure for real HLR, so an adaptive heuristic is the
 * honest, deliverable substitute. Not a real regression - documented as such deliberately.
 */
public final class AdaptiveHalfLifeModel {

	private static final double MIN_EASE_FACTOR = 1.3;
	private static final double MAX_EASE_FACTOR = 3.0;
	private static final double MIN_HALF_LIFE_DAYS = 0.5;
	private static final double EASE_FACTOR_GROWTH_STEP = 0.1;
	private static final double EASE_FACTOR_SHRINK_STEP = 0.2;
	private static final double INCORRECT_HALF_LIFE_MULTIPLIER = 0.5;

	private AdaptiveHalfLifeModel() {
	}

	/**
	 * Retention-decay "forgetting" probability given how long it's been since the item was last
	 * reviewed, using the half-life as it stood BEFORE the current attempt - callers must not pass
	 * a half-life already updated by {@link #updateHalfLife} for the same attempt, or the
	 * recurrence signal collapses to ~0 (see WeakPointScoringEngine's ordering contract).
	 */
	public static double forgetting(double daysSincePriorReview, double priorHalfLifeDays) {
		double days = Math.max(0.0, daysSincePriorReview);
		double retention = Math.exp(-days / priorHalfLifeDays);
		return 1.0 - retention;
	}

	/**
	 * Continuous ("partial-credit") variant of {@link #updateHalfLife}: blends the correct- and
	 * incorrect-outcome half-life/ease results by {@code score} in [0,1], so it reduces EXACTLY to
	 * the binary update at {@code score = 1.0}/{@code 0.0}. For scale-graded skills (e.g. pronunciation).
	 */
	public static ScoringState updateHalfLifeContinuous(ScoringState prior, double score) {
		double clamped = Math.min(1.0, Math.max(0.0, score));
		ScoringState ifCorrect = updateHalfLife(prior, true);
		ScoringState ifIncorrect = updateHalfLife(prior, false);
		return ScoringState.builder()
				.halfLifeDays(clamped * ifCorrect.getHalfLifeDays() + (1 - clamped) * ifIncorrect.getHalfLifeDays())
				.easeFactor(clamped * ifCorrect.getEaseFactor() + (1 - clamped) * ifIncorrect.getEaseFactor())
				.mastery(prior.getMastery())
				.leitnerBox(prior.getLeitnerBox())
				.daysSincePriorReview(0.0)
				.build();
	}

	/** Derives the next half-life/ease-factor pair from the current attempt's outcome. */
	public static ScoringState updateHalfLife(ScoringState prior, boolean correct) {
		double easeFactor;
		double halfLifeDays;
		if (correct) {
			easeFactor = Math.min(MAX_EASE_FACTOR, prior.getEaseFactor() + EASE_FACTOR_GROWTH_STEP);
			halfLifeDays = prior.getHalfLifeDays() * prior.getEaseFactor();
		} else {
			easeFactor = Math.max(MIN_EASE_FACTOR, prior.getEaseFactor() - EASE_FACTOR_SHRINK_STEP);
			halfLifeDays = prior.getHalfLifeDays() * INCORRECT_HALF_LIFE_MULTIPLIER;
		}
		halfLifeDays = Math.max(MIN_HALF_LIFE_DAYS, halfLifeDays);

		return ScoringState.builder()
				.halfLifeDays(halfLifeDays)
				.easeFactor(easeFactor)
				.mastery(prior.getMastery())
				.leitnerBox(prior.getLeitnerBox())
				.daysSincePriorReview(0.0)
				.build();
	}
}
