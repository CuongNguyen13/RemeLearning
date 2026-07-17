package com.remelearning.english.practice.scoring.impl;

import com.remelearning.common.scoring.AttemptOutcome;
import com.remelearning.common.scoring.BktParams;
import com.remelearning.common.scoring.LabelKeys;
import com.remelearning.common.scoring.PopulationStats;
import com.remelearning.common.scoring.ScoringResult;
import com.remelearning.common.scoring.ScoringState;
import com.remelearning.common.scoring.WeakPointScoringEngine;
import com.remelearning.english.practice.domain.MistakeHistoryEntry;
import com.remelearning.english.practice.dto.PracticeAttemptRequest;
import com.remelearning.english.practice.mapper.ItemDifficultyStatsMapper;
import com.remelearning.english.practice.mapper.MistakeHistoryMapper;
import com.remelearning.english.practice.scoring.WeakPointDispatcher;
import com.remelearning.english.practice.scoring.WeakPointScoreUpdate;
import com.remelearning.english.practice.scoring.WeakPointScoringOrchestrator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class WeakPointScoringOrchestratorImpl implements WeakPointScoringOrchestrator {

	private static final double DEFAULT_HALF_LIFE_DAYS = 7.0;
	private static final double DEFAULT_EASE_FACTOR = 2.5;
	private static final int DEFAULT_LEITNER_BOX = 1;
	// Cold-start seed denominator: pre-existing mistake_history rows all start at a flat
	// mastery=0.3 default regardless of real occurrence_count, so the first time the Java engine
	// scores one, seed mastery from occurrence_count instead of using the flat default - otherwise
	// a long-troubled item looks like a fresh cold start the first time it's touched post-migration.
	private static final double COLD_START_SMOOTHING = 3.0;
	private static final double COLD_START_MIN_MASTERY = 0.05;
	private static final double COLD_START_MAX_MASTERY = 0.6;

	private final MistakeHistoryMapper mistakeHistoryMapper;
	private final ItemDifficultyStatsMapper itemDifficultyStatsMapper;
	private final Map<String, BktParams> bktParamsByCategory;
	private final WeakPointDispatcher weakPointDispatcher;

	// Scores one attempt directly against the Java engine and pushes the result into the owning
	// domain, bypassing the ai-service/Kafka round-trip entirely for this item.
	@Override
	@Transactional
	public void scoreAttempt(String userId, String recordingId, PracticeAttemptRequest attempt, boolean recurredInBatch) {
		Instant now = Instant.now();

		// Locks the row (SELECT ... FOR UPDATE) and reads state as it stood BEFORE this attempt -
		// must happen before recordAttempt below touches last_seen_at, or the recency signal this
		// attempt is scored against would already be reset to ~0 (see WeakPointScoringEngine's
		// ordering contract).
		MistakeHistoryEntry priorEntry = mistakeHistoryMapper.findOneForUpdate(userId, attempt.getItemId()).orElse(null);
		ScoringState priorState = toScoringState(priorEntry, now);

		mistakeHistoryMapper.recordAttempt(userId, attempt.getItemId(), attempt.getCategory(), attempt.getLabel(), attempt.isCorrect());

		String labelKey = LabelKeys.normalize(attempt.getLabel());
		PopulationStats populationStats = itemDifficultyStatsMapper.find(attempt.getCategory(), labelKey)
				.orElseGet(() -> PopulationStats.builder().build());
		BktParams bktParams = bktParamsByCategory.getOrDefault(attempt.getCategory().toLowerCase(), BktParams.DEFAULT);

		AttemptOutcome outcome = AttemptOutcome.builder().correct(attempt.isCorrect()).recurredInBatch(recurredInBatch).build();
		ScoringResult result = WeakPointScoringEngine.scoreAfterAttempt(priorState, outcome, populationStats, bktParams, now);

		mistakeHistoryMapper.updateScoringState(userId, attempt.getItemId(),
				result.getUpdatedState().getEaseFactor(), result.getUpdatedState().getHalfLifeDays(),
				result.getUpdatedState().getMastery(), result.getUpdatedState().getLeitnerBox(),
				result.getNextReviewAt(), result.getWeakScore(), labelKey);

		itemDifficultyStatsMapper.upsertIncrement(attempt.getCategory(), labelKey,
				attempt.isCorrect() ? 1 : 0, attempt.isCorrect() ? 0 : 1);

		weakPointDispatcher.dispatch(WeakPointScoreUpdate.builder()
				.recordingId(recordingId)
				.userId(userId)
				.itemId(attempt.getItemId())
				.category(attempt.getCategory())
				.label(attempt.getLabel())
				.weakScore(result.getWeakScore())
				.masteryLevel(result.getUpdatedState().getMastery())
				.nextReviewAt(result.getNextReviewAt())
				.build());

		log.debug("Java-scored item {} for user {}: weakScore={}, mastery={}, nextReviewAt={}",
				attempt.getItemId(), userId, result.getWeakScore(), result.getUpdatedState().getMastery(), result.getNextReviewAt());
	}

	// Builds the PRE-attempt ScoringState from the locked mistake_history row, applying cold-start
	// defaults for a brand-new item and the smarter cold-start mastery seed (see COLD_START_*
	// constants) the first time an already-existing row is scored by the Java engine.
	private ScoringState toScoringState(MistakeHistoryEntry entry, Instant now) {
		if (entry == null) {
			return ScoringState.builder()
					.halfLifeDays(DEFAULT_HALF_LIFE_DAYS)
					.easeFactor(DEFAULT_EASE_FACTOR)
					.mastery(BktParams.DEFAULT.getPInit())
					.leitnerBox(DEFAULT_LEITNER_BOX)
					.daysSincePriorReview(0.0)
					.build();
		}

		double daysSincePriorReview = entry.getLastSeenAt() == null ? 0.0
				: Math.max(0.0, Duration.between(entry.getLastSeenAt(), now).toSeconds() / 86400.0);

		boolean neverScoredByJavaEngine = entry.getNextReviewAt() == null;
		double mastery = neverScoredByJavaEngine
				? coldStartMastery(entry.getOccurrenceCount())
				: entry.getMastery();

		return ScoringState.builder()
				.halfLifeDays(entry.getHalfLifeDays() > 0 ? entry.getHalfLifeDays() : DEFAULT_HALF_LIFE_DAYS)
				.easeFactor(entry.getEaseFactor() > 0 ? entry.getEaseFactor() : DEFAULT_EASE_FACTOR)
				.mastery(mastery)
				.leitnerBox(entry.getLeitnerBox() > 0 ? entry.getLeitnerBox() : DEFAULT_LEITNER_BOX)
				.daysSincePriorReview(daysSincePriorReview)
				.build();
	}

	// Seeds mastery from existing occurrence_count instead of a flat default, so a long-troubled
	// item doesn't look like a fresh cold start the first time the Java engine scores it.
	private double coldStartMastery(int occurrenceCount) {
		double raw = 1.0 - occurrenceCount / (occurrenceCount + COLD_START_SMOOTHING);
		return Math.min(COLD_START_MAX_MASTERY, Math.max(COLD_START_MIN_MASTERY, raw));
	}
}
