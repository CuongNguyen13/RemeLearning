package com.remelearning.common.scoring;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AdaptiveHalfLifeModelTest {

	@Test
	void forgettingIsZeroImmediatelyAfterReview() {
		assertThat(AdaptiveHalfLifeModel.forgetting(0.0, 7.0)).isEqualTo(0.0);
	}

	@Test
	void forgettingApproachesOneAsDaysGrow() {
		double soon = AdaptiveHalfLifeModel.forgetting(1.0, 7.0);
		double later = AdaptiveHalfLifeModel.forgetting(30.0, 7.0);
		assertThat(later).isGreaterThan(soon);
		assertThat(later).isCloseTo(1.0, org.assertj.core.data.Offset.offset(0.05));
	}

	@Test
	void negativeDaysAreFlooredAtZero() {
		assertThat(AdaptiveHalfLifeModel.forgetting(-5.0, 7.0)).isEqualTo(0.0);
	}

	@Test
	void correctAnswerGrowsHalfLifeAndEaseFactor() {
		ScoringState prior = ScoringState.builder().halfLifeDays(7.0).easeFactor(2.5).mastery(0.3).leitnerBox(1).build();

		ScoringState updated = AdaptiveHalfLifeModel.updateHalfLife(prior, true);

		assertThat(updated.getEaseFactor()).isGreaterThan(prior.getEaseFactor());
		assertThat(updated.getHalfLifeDays()).isEqualTo(7.0 * 2.5);
	}

	@Test
	void incorrectAnswerShrinksHalfLifeAndEaseFactor() {
		ScoringState prior = ScoringState.builder().halfLifeDays(7.0).easeFactor(2.5).mastery(0.3).leitnerBox(1).build();

		ScoringState updated = AdaptiveHalfLifeModel.updateHalfLife(prior, false);

		assertThat(updated.getEaseFactor()).isLessThan(prior.getEaseFactor());
		assertThat(updated.getHalfLifeDays()).isEqualTo(7.0 * 0.5);
	}

	@Test
	void easeFactorNeverDropsBelowFloor() {
		ScoringState prior = ScoringState.builder().halfLifeDays(1.0).easeFactor(1.35).mastery(0.3).leitnerBox(1).build();

		ScoringState updated = AdaptiveHalfLifeModel.updateHalfLife(prior, false);

		assertThat(updated.getEaseFactor()).isEqualTo(1.3);
	}

	@Test
	void easeFactorNeverExceedsCeiling() {
		ScoringState prior = ScoringState.builder().halfLifeDays(1.0).easeFactor(2.95).mastery(0.3).leitnerBox(1).build();

		ScoringState updated = AdaptiveHalfLifeModel.updateHalfLife(prior, true);

		assertThat(updated.getEaseFactor()).isEqualTo(3.0);
	}

	@Test
	void halfLifeNeverDropsBelowFloor() {
		ScoringState prior = ScoringState.builder().halfLifeDays(0.6).easeFactor(1.3).mastery(0.3).leitnerBox(1).build();

		ScoringState updated = AdaptiveHalfLifeModel.updateHalfLife(prior, false);

		assertThat(updated.getHalfLifeDays()).isEqualTo(0.5);
	}
}
