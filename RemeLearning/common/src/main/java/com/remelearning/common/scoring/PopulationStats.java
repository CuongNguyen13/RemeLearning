package com.remelearning.common.scoring;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Aggregate correct/incorrect counts for one mistake type across every learner, used as the
 * population-level input to {@link RaschDifficultyEstimator} - item difficulty (unlike mastery)
 * is a property of the item, not of any one learner. Carries a no-args constructor (alongside
 * {@code @Builder}) so it can double as a MyBatis result type in callers that persist it.
 */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PopulationStats {

	@Builder.Default
	private long correctCount = 0;
	@Builder.Default
	private long incorrectCount = 0;
}
