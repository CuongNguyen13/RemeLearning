package com.remelearning.dashboard.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * A single learner's recurring/forgotten item, derived from ai-service's
 * {@code learning.gap.analyzed} event. Unlike english-service (one table per category), this is a
 * single unified table across all 3 categories (vocabulary/grammar/pronunciation), with
 * {@code category} kept as a plain column so per-category rollups can be computed at read time.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WeakPointSnapshot {
	private Long id;
	private String userId;
	private String recordingId;
	private String itemId;
	private String category;
	private String label;
	private double forgettingScore;
	private String recommendation;
	private Instant updatedAt;
}
