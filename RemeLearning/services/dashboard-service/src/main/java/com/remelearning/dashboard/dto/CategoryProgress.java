package com.remelearning.dashboard.dto;

import java.time.Instant;

/**
 * Per-category rollup of a learner's weak points, computed at read time via a
 * {@code GROUP BY category} SQL query over {@code weak_points_snapshot} (not a maintained
 * running counter) - the {@code resultType} of
 * {@link com.remelearning.dashboard.mapper.WeakPointSnapshotMapper#selectProgressSummary}.
 */
public record CategoryProgress(
		String category,
		long weakPointCount,
		double avgForgettingScore,
		Instant lastUpdated) {
}
