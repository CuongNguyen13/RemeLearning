package com.remelearning.english.practice.mapper;

import com.remelearning.common.scoring.PopulationStats;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.Optional;

/**
 * Population-level (cross-user) correct/incorrect counters per (category, labelKey), feeding
 * {@link com.remelearning.common.scoring.RaschDifficultyEstimator}. Deliberately separate from
 * mistake_history, which is per-user - item difficulty is a property of the item, not the learner.
 */
@Mapper
public interface ItemDifficultyStatsMapper {

	/** Empty when this exact (category, labelKey) has never been attempted by anyone yet (cold start). */
	Optional<PopulationStats> find(@Param("category") String category, @Param("labelKey") String labelKey);

	/** Adds the given correct/incorrect deltas onto the running population totals, upserting a fresh row if absent. */
	void upsertIncrement(@Param("category") String category, @Param("labelKey") String labelKey,
			@Param("correctDelta") long correctDelta, @Param("incorrectDelta") long incorrectDelta);
}
