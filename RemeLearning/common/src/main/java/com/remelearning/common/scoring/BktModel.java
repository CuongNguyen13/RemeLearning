package com.remelearning.common.scoring;

/**
 * Bayesian Knowledge Tracing (Corbett &amp; Anderson, 1994): a Hidden Markov Model over a binary
 * "mastered / not mastered" latent state, updated in closed form after each observed
 * correct/incorrect attempt. Unlike raw occurrence counting, mastery here is bounded in [0,1] and
 * accounts for slip (mistakes despite mastery) and guess (correct answers despite no mastery).
 */
public final class BktModel {

	private BktModel() {
	}

	/**
	 * Continuous ("partial-credit") variant for graded-on-a-scale skills like pronunciation, where an
	 * attempt isn't cleanly right/wrong but scores {@code score} in [0,1]. Blends the correct- and
	 * incorrect-evidence posteriors by that score, so it reduces EXACTLY to {@link #updateMastery}
	 * at {@code score = 1.0} (correct) and {@code score = 0.0} (incorrect). This is the "continuous
	 * scoring adapter" for luyện nói, kept additive so the binary path is untouched.
	 */
	public static double updateMasteryContinuous(double priorMastery, double score, BktParams params) {
		double clamped = Math.min(1.0, Math.max(0.0, score));
		double masteryIfCorrect = updateMastery(priorMastery, true, params);
		double masteryIfIncorrect = updateMastery(priorMastery, false, params);
		return clamped * masteryIfCorrect + (1 - clamped) * masteryIfIncorrect;
	}

	/** Posterior P(mastered) after observing one attempt, given the prior and this attempt's outcome. */
	public static double updateMastery(double priorMastery, boolean correct, BktParams params) {
		double p = priorMastery;
		double evidence;
		if (correct) {
			double numerator = p * (1 - params.getPSlip());
			double denominator = numerator + (1 - p) * params.getPGuess();
			evidence = numerator / denominator;
		} else {
			double numerator = p * params.getPSlip();
			double denominator = numerator + (1 - p) * (1 - params.getPGuess());
			evidence = numerator / denominator;
		}
		double posterior = evidence + (1 - evidence) * params.getPTransit();
		return Math.min(1.0, Math.max(0.0, posterior));
	}
}
