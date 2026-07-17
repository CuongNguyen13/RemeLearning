package com.remelearning.common.scoring;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class LeitnerSchedulerTest {

	private final Instant now = Instant.parse("2026-01-01T00:00:00Z");

	@Test
	void correctAnswerAdvancesBox() {
		LeitnerResult result = LeitnerScheduler.next(2, true, now);

		assertThat(result.getBox()).isEqualTo(3);
	}

	@Test
	void boxNeverExceedsFive() {
		LeitnerResult result = LeitnerScheduler.next(5, true, now);

		assertThat(result.getBox()).isEqualTo(5);
	}

	@Test
	void incorrectAnswerResetsToBoxOne() {
		LeitnerResult result = LeitnerScheduler.next(4, false, now);

		assertThat(result.getBox()).isEqualTo(1);
	}

	@Test
	void nextReviewAtStrictlyIncreasesWithBox() {
		Instant box1 = LeitnerScheduler.next(1, false, now).getNextReviewAt();
		Instant box3 = LeitnerScheduler.next(2, true, now).getNextReviewAt();
		Instant box5 = LeitnerScheduler.next(4, true, now).getNextReviewAt();

		assertThat(box1).isBefore(box3);
		assertThat(box3).isBefore(box5);
	}
}
