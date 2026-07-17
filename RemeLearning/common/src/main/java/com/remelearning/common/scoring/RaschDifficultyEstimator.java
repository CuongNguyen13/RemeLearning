package com.remelearning.common.scoring;

/**
 * A single-parameter, Rasch-inspired difficulty weight for an item, estimated from how the whole
 * learner population (not just one user) has fared against it - true IRT calibrates a difficulty
 * parameter via maximum-likelihood over many responses; this is a Laplace-smoothed log-odds
 * approximation of that same idea, cheap enough to compute from a running correct/incorrect
 * counter with no offline fitting step.
 */
public final class RaschDifficultyEstimator {

	/** Laplace smoothing constant so a brand-new item (0 correct, 0 incorrect) starts at weight ~1.0. */
	private static final double SMOOTHING = 1.0;
	/** Controls how sharply the weight reacts to the population's error rate. */
	private static final double SCALE = 2.0;
	private static final double MIN_WEIGHT = 0.5;
	private static final double MAX_WEIGHT = 2.0;

	private RaschDifficultyEstimator() {
	}

	/** Weight centered at 1.0; &gt;1 for items the population misses more often, &lt;1 for easy ones. */
	public static double difficultyWeight(PopulationStats stats) {
		double logOdds = Math.log((stats.getIncorrectCount() + SMOOTHING) / (double) (stats.getCorrectCount() + SMOOTHING));
		double weight = 1.0 + Math.tanh(logOdds / SCALE);
		return Math.min(MAX_WEIGHT, Math.max(MIN_WEIGHT, weight));
	}
}
