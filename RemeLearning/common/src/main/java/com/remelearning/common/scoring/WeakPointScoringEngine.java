package com.remelearning.common.scoring;

import java.time.Instant;

/**
 * Combines three independently-established models into one bounded weak-point score, instead of
 * the single Ebbinghaus formula used elsewhere in this system:
 * {@code weakScore = difficultyWeight x forgetting x masteryGap x recurrenceBoost}. Both
 * `difficultyWeight` and `masteryGap` are clamped so that one noisy estimate (a cold-start Rasch
 * bucket, a confident-but-wrong BKT read) can't zero out or blow up the score.
 *
 * <p>Ordering contract callers MUST follow: {@code priorState} must reflect the item's state
 * BEFORE this attempt (in particular {@code daysSincePriorReview} computed against the
 * last-review timestamp as it stood before this attempt touched it). Scoring first, then deriving
 * updated state from the same attempt, is what keeps the same-batch recurrence signal meaningful -
 * scoring against already-updated state would make {@code forgetting} collapse to ~0 for any item
 * just touched, silently defeating the recurrence boost.
 */
public final class WeakPointScoringEngine {

	private static final double RECURRENCE_BOOST = 1.5;
	/** Floor on (1 - mastery) so a single confident BKT read can't drive the score to exactly 0. */
	private static final double MIN_MASTERY_GAP = 0.15;
	/** A continuous attempt at/above this score is treated as "correct" for the Leitner promotion. */
	private static final double LEITNER_CORRECT_THRESHOLD = 0.5;

	private WeakPointScoringEngine() {
	}

	/** Scores this attempt against the prior state, then derives the post-attempt state to persist - see the ordering contract above. */
	public static ScoringResult scoreAfterAttempt(ScoringState priorState, AttemptOutcome outcome,
			PopulationStats populationStats, BktParams bktParams, Instant now) {
		double forgetting = AdaptiveHalfLifeModel.forgetting(priorState.getDaysSincePriorReview(), priorState.getHalfLifeDays());
		double recurrenceBoost = outcome.isRecurredInBatch() ? RECURRENCE_BOOST : 1.0;
		double difficultyWeight = RaschDifficultyEstimator.difficultyWeight(populationStats);
		double masteryGap = Math.max(MIN_MASTERY_GAP, 1.0 - priorState.getMastery());

		double weakScore = difficultyWeight * forgetting * masteryGap * recurrenceBoost;

		ScoringState halfLifeUpdate = AdaptiveHalfLifeModel.updateHalfLife(priorState, outcome.isCorrect());
		double updatedMastery = BktModel.updateMastery(priorState.getMastery(), outcome.isCorrect(), bktParams);
		LeitnerResult leitnerResult = LeitnerScheduler.next(priorState.getLeitnerBox(), outcome.isCorrect(), now);

		ScoringState updatedState = ScoringState.builder()
				.halfLifeDays(halfLifeUpdate.getHalfLifeDays())
				.easeFactor(halfLifeUpdate.getEaseFactor())
				.mastery(updatedMastery)
				.leitnerBox(leitnerResult.getBox())
				.daysSincePriorReview(0.0)
				.build();

		return ScoringResult.builder()
				.weakScore(weakScore)
				.updatedState(updatedState)
				.nextReviewAt(leitnerResult.getNextReviewAt())
				.build();
	}

	/**
	 * Continuous ("partial-credit") counterpart of {@link #scoreAfterAttempt} for scale-graded skills
	 * like pronunciation: the weak-score formula is identical (it reads only pre-attempt state), but
	 * mastery/half-life update via the continuous BKT/half-life variants and the Leitner box promotes
	 * only when {@code score >= LEITNER_CORRECT_THRESHOLD}. Reduces exactly to the binary path at
	 * {@code score = 1.0}/{@code 0.0}, so a binary caller could use either interchangeably.
	 */
	public static ScoringResult scoreAfterContinuousAttempt(ScoringState priorState, ContinuousAttemptOutcome outcome,
			PopulationStats populationStats, BktParams bktParams, Instant now) {
		double forgetting = AdaptiveHalfLifeModel.forgetting(priorState.getDaysSincePriorReview(), priorState.getHalfLifeDays());
		double recurrenceBoost = outcome.isRecurredInBatch() ? RECURRENCE_BOOST : 1.0;
		double difficultyWeight = RaschDifficultyEstimator.difficultyWeight(populationStats);
		double masteryGap = Math.max(MIN_MASTERY_GAP, 1.0 - priorState.getMastery());

		double weakScore = difficultyWeight * forgetting * masteryGap * recurrenceBoost;

		ScoringState halfLifeUpdate = AdaptiveHalfLifeModel.updateHalfLifeContinuous(priorState, outcome.getScore());
		double updatedMastery = BktModel.updateMasteryContinuous(priorState.getMastery(), outcome.getScore(), bktParams);
		LeitnerResult leitnerResult = LeitnerScheduler.next(
				priorState.getLeitnerBox(), outcome.getScore() >= LEITNER_CORRECT_THRESHOLD, now);

		ScoringState updatedState = ScoringState.builder()
				.halfLifeDays(halfLifeUpdate.getHalfLifeDays())
				.easeFactor(halfLifeUpdate.getEaseFactor())
				.mastery(updatedMastery)
				.leitnerBox(leitnerResult.getBox())
				.daysSincePriorReview(0.0)
				.build();

		return ScoringResult.builder()
				.weakScore(weakScore)
				.updatedState(updatedState)
				.nextReviewAt(leitnerResult.getNextReviewAt())
				.build();
	}
}
