package com.remelearning.common.scoring;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RaschDifficultyEstimatorTest {

	@Test
	void weightIsApproximatelyOneAtColdStart() {
		PopulationStats stats = PopulationStats.builder().correctCount(0).incorrectCount(0).build();

		assertThat(RaschDifficultyEstimator.difficultyWeight(stats)).isEqualTo(1.0);
	}

	@Test
	void weightIsApproximatelyOneWhenEvenlySplit() {
		PopulationStats stats = PopulationStats.builder().correctCount(50).incorrectCount(50).build();

		assertThat(RaschDifficultyEstimator.difficultyWeight(stats)).isEqualTo(1.0);
	}

	@Test
	void weightExceedsOneWhenPopulationMissesItMoreOften() {
		PopulationStats stats = PopulationStats.builder().correctCount(5).incorrectCount(95).build();

		assertThat(RaschDifficultyEstimator.difficultyWeight(stats)).isGreaterThan(1.0);
	}

	@Test
	void weightIsBelowOneWhenPopulationGetsItRightMoreOften() {
		PopulationStats stats = PopulationStats.builder().correctCount(95).incorrectCount(5).build();

		assertThat(RaschDifficultyEstimator.difficultyWeight(stats)).isLessThan(1.0);
	}

	@Test
	void weightIsBoundedAtExtremes() {
		PopulationStats allWrong = PopulationStats.builder().correctCount(0).incorrectCount(100_000).build();
		PopulationStats allRight = PopulationStats.builder().correctCount(100_000).incorrectCount(0).build();

		assertThat(RaschDifficultyEstimator.difficultyWeight(allWrong)).isLessThanOrEqualTo(2.0);
		assertThat(RaschDifficultyEstimator.difficultyWeight(allRight)).isGreaterThanOrEqualTo(0.5);
	}
}
