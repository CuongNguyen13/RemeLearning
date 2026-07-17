package com.remelearning.common.scoring;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class BktModelTest {

	@Test
	void correctAnswerIncreasesMasteryMoreThanIncorrectAnswer() {
		double afterCorrect = BktModel.updateMastery(0.3, true, BktParams.DEFAULT);
		double afterIncorrect = BktModel.updateMastery(0.3, false, BktParams.DEFAULT);

		assertThat(afterCorrect).isGreaterThan(afterIncorrect);
	}

	@Test
	void masteryStaysWithinBoundsOverManyUpdates() {
		double mastery = 0.3;
		for (int i = 0; i < 100; i++) {
			mastery = BktModel.updateMastery(mastery, i % 3 != 0, BktParams.DEFAULT);
			assertThat(mastery).isBetween(0.0, 1.0);
		}
	}

	@Test
	void matchesHandComputedValueForFixedInputs() {
		// p=0.5, correct: evidence = 0.5*(1-0.1) / (0.5*0.9 + 0.5*0.2) = 0.45 / 0.55 = 0.818181...
		// posterior = 0.818181... + (1 - 0.818181...) * 0.1 = 0.818181... + 0.018181... = 0.836363...
		double posterior = BktModel.updateMastery(0.5, true, BktParams.DEFAULT);

		assertThat(posterior).isCloseTo(0.836363, org.assertj.core.data.Offset.offset(0.0001));
	}
}
