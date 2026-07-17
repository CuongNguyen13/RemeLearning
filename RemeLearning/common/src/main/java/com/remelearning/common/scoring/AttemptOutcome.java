package com.remelearning.common.scoring;

import lombok.Builder;
import lombok.Getter;

/** The single fact the scoring engine needs about one graded attempt at an item. */
@Getter
@Builder
public class AttemptOutcome {

	private boolean correct;
	/**
	 * True when this same item already appeared earlier in the same batch of attempts being
	 * graded together - the redo flow has no transcript to re-scan for a mention, unlike the
	 * recording-analysis pipeline, so recurrence here means "seen again in this batch".
	 */
	private boolean recurredInBatch;
}
