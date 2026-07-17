package com.remelearning.common.scoring;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class WeakPointScoringEngineTest {

	private final Instant now = Instant.parse("2026-01-01T00:00:00Z");
	private final PopulationStats neutralStats = PopulationStats.builder().correctCount(10).incorrectCount(10).build();

	@Test
	void sameBatchRecurrenceProducesNonZeroBoostedScore() {
		// The item was last reviewed 10 days ago (pre-attempt state) and is being answered wrong
		// again in this same batch - this is exactly the case RECURRENCE_BOOST exists for. The
		// score must be computed against the PRE-attempt half-life/recency, not the post-attempt
		// reset, or it would collapse to ~0 (the bug this test guards against).
		ScoringState priorState = ScoringState.builder()
				.halfLifeDays(7.0)
				.easeFactor(2.5)
				.mastery(0.3)
				.leitnerBox(1)
				.daysSincePriorReview(10.0)
				.build();
		AttemptOutcome recurring = AttemptOutcome.builder().correct(false).recurredInBatch(true).build();
		AttemptOutcome nonRecurring = AttemptOutcome.builder().correct(false).recurredInBatch(false).build();

		ScoringResult recurringResult = WeakPointScoringEngine.scoreAfterAttempt(priorState, recurring, neutralStats, BktParams.DEFAULT, now);
		ScoringResult nonRecurringResult = WeakPointScoringEngine.scoreAfterAttempt(priorState, nonRecurring, neutralStats, BktParams.DEFAULT, now);

		assertThat(recurringResult.getWeakScore()).isGreaterThan(0.0);
		assertThat(recurringResult.getWeakScore()).isGreaterThan(nonRecurringResult.getWeakScore());
	}

	@Test
	void higherMasteryProducesLowerScoreForSamePriorRecency() {
		ScoringState lowMastery = ScoringState.builder().halfLifeDays(7.0).easeFactor(2.5).mastery(0.1).leitnerBox(1).daysSincePriorReview(10.0).build();
		ScoringState highMastery = ScoringState.builder().halfLifeDays(7.0).easeFactor(2.5).mastery(0.9).leitnerBox(1).daysSincePriorReview(10.0).build();
		AttemptOutcome outcome = AttemptOutcome.builder().correct(false).recurredInBatch(false).build();

		double lowMasteryScore = WeakPointScoringEngine.scoreAfterAttempt(lowMastery, outcome, neutralStats, BktParams.DEFAULT, now).getWeakScore();
		double highMasteryScore = WeakPointScoringEngine.scoreAfterAttempt(highMastery, outcome, neutralStats, BktParams.DEFAULT, now).getWeakScore();

		assertThat(lowMasteryScore).isGreaterThan(highMasteryScore);
	}

	@Test
	void updatedStateReflectsTheAttemptOutcomeNotThePriorState() {
		ScoringState priorState = ScoringState.builder().halfLifeDays(7.0).easeFactor(2.5).mastery(0.3).leitnerBox(2).daysSincePriorReview(3.0).build();
		AttemptOutcome correct = AttemptOutcome.builder().correct(true).recurredInBatch(false).build();

		ScoringResult result = WeakPointScoringEngine.scoreAfterAttempt(priorState, correct, neutralStats, BktParams.DEFAULT, now);

		assertThat(result.getUpdatedState().getLeitnerBox()).isEqualTo(3);
		assertThat(result.getUpdatedState().getMastery()).isGreaterThan(priorState.getMastery());
		assertThat(result.getUpdatedState().getHalfLifeDays()).isGreaterThan(priorState.getHalfLifeDays());
		assertThat(result.getNextReviewAt()).isAfter(now);
	}
}
