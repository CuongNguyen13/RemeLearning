package com.remelearning.common.scoring;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/**
 * The continuous ("partial-credit") scoring path for scale-graded skills (e.g. pronunciation) must
 * reduce exactly to the binary path at the endpoints score=1.0/0.0, and interpolate between them.
 */
class ContinuousScoringTest {

	private static final Instant NOW = Instant.parse("2026-01-01T00:00:00Z");

	private ScoringState state() {
		return ScoringState.builder()
				.halfLifeDays(7.0).easeFactor(2.5).mastery(0.3).leitnerBox(2).daysSincePriorReview(7.0).build();
	}

	// Continuous BKT at score 1.0/0.0 equals the binary correct/incorrect update.
	@Test
	void continuousMasteryReducesToBinaryAtEndpoints() {
		assertThat(BktModel.updateMasteryContinuous(0.3, 1.0, BktParams.DEFAULT))
				.isEqualTo(BktModel.updateMastery(0.3, true, BktParams.DEFAULT));
		assertThat(BktModel.updateMasteryContinuous(0.3, 0.0, BktParams.DEFAULT))
				.isEqualTo(BktModel.updateMastery(0.3, false, BktParams.DEFAULT));
	}

	// A mid score lands strictly between the incorrect and correct posteriors.
	@Test
	void continuousMasteryInterpolatesForMidScore() {
		double low = BktModel.updateMastery(0.3, false, BktParams.DEFAULT);
		double high = BktModel.updateMastery(0.3, true, BktParams.DEFAULT);
		double mid = BktModel.updateMasteryContinuous(0.3, 0.5, BktParams.DEFAULT);
		assertThat(mid).isGreaterThan(low).isLessThan(high).isEqualTo((low + high) / 2, within(1e-9));
	}

	// The full continuous engine result equals the binary engine result at the endpoints.
	@Test
	void continuousEngineReducesToBinaryEngineAtEndpoints() {
		PopulationStats pop = PopulationStats.builder().correctCount(3).incorrectCount(5).build();

		ScoringResult binaryCorrect = WeakPointScoringEngine.scoreAfterAttempt(
				state(), AttemptOutcome.builder().correct(true).recurredInBatch(false).build(), pop, BktParams.DEFAULT, NOW);
		ScoringResult continuousOne = WeakPointScoringEngine.scoreAfterContinuousAttempt(
				state(), ContinuousAttemptOutcome.builder().score(1.0).recurredInBatch(false).build(), pop, BktParams.DEFAULT, NOW);

		assertThat(continuousOne.getWeakScore()).isEqualTo(binaryCorrect.getWeakScore());
		assertThat(continuousOne.getUpdatedState().getMastery()).isEqualTo(binaryCorrect.getUpdatedState().getMastery());
		assertThat(continuousOne.getUpdatedState().getHalfLifeDays()).isEqualTo(binaryCorrect.getUpdatedState().getHalfLifeDays());
		assertThat(continuousOne.getUpdatedState().getLeitnerBox()).isEqualTo(binaryCorrect.getUpdatedState().getLeitnerBox());
		assertThat(continuousOne.getNextReviewAt()).isEqualTo(binaryCorrect.getNextReviewAt());
	}

	// A low continuous score demotes the Leitner box to 1, like a binary incorrect answer.
	@Test
	void continuousLowScoreDemotesLeitnerBox() {
		ScoringResult result = WeakPointScoringEngine.scoreAfterContinuousAttempt(
				state(), ContinuousAttemptOutcome.builder().score(0.2).recurredInBatch(false).build(),
				PopulationStats.builder().build(), BktParams.DEFAULT, NOW);
		assertThat(result.getUpdatedState().getLeitnerBox()).isEqualTo(1);
	}
}
