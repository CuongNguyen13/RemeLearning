package com.remelearning.common.scoring;

import lombok.Builder;
import lombok.Getter;

import java.time.Instant;

/** Output of {@link LeitnerScheduler#next}: the box an item moved to and when it's due again. */
@Getter
@Builder
public class LeitnerResult {

	private int box;
	private Instant nextReviewAt;
}
