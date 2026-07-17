package com.remelearning.common.scoring;

import lombok.Builder;
import lombok.Getter;

/**
 * The four classic Bayesian Knowledge Tracing parameters (Corbett &amp; Anderson, 1994). Callers
 * should supply a set tuned per category rather than assuming one size fits all - e.g. "guessing"
 * a correct pronunciation isn't the same phenomenon as guessing a multiple-choice grammar answer,
 * so {@code pGuess} should be much lower for pronunciation-style domains.
 */
@Getter
@Builder
public class BktParams {

	/** P(already mastered) before any evidence - prior probability. */
	private double pInit;
	/** P(transitions from not-mastered to mastered) after one practice opportunity. */
	private double pTransit;
	/** P(answers incorrectly despite being mastered) - a "slip". */
	private double pSlip;
	/** P(answers correctly despite not being mastered) - a "guess". */
	private double pGuess;

	/** Reasonable literature-typical defaults for a generic (non-guessable) skill. */
	public static final BktParams DEFAULT = BktParams.builder()
			.pInit(0.3)
			.pTransit(0.1)
			.pSlip(0.1)
			.pGuess(0.2)
			.build();
}
