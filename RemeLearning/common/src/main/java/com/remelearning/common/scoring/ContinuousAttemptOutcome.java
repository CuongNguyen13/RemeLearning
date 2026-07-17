package com.remelearning.common.scoring;

import lombok.Builder;
import lombok.Getter;

/**
 * A graded attempt at an item scored on a continuous scale rather than binary right/wrong - for
 * skills like pronunciation ("luyện nói") where a phoneme/word can be partly correct. {@code score}
 * is in [0,1]. Consumed by {@link WeakPointScoringEngine#scoreAfterContinuousAttempt}.
 */
@Getter
@Builder
public class ContinuousAttemptOutcome {

	/** Pronunciation/quality score in [0,1]; 1.0 == fully correct, 0.0 == fully incorrect. */
	private double score;
	/** True when this item already appeared earlier in the same batch being graded together. */
	private boolean recurredInBatch;
}
