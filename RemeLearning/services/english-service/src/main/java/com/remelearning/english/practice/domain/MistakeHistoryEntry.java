package com.remelearning.english.practice.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * A learner's recurring-mistake history for one item, across whichever domain (vocabulary/
 * grammar/pronunciation) it belongs to. Seeded the first time an item is ever surfaced by
 * {@code learning.gap.analyzed} and updated afterward only by graded redo attempts
 * ({@link com.remelearning.english.practice.service.PracticeService}), so it can be bundled
 * into an {@code AnalysisRequestedEvent} for ai-service's {@code RuleBasedAnalyzer}.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MistakeHistoryEntry {
	private Long id;
	private String userId;
	private String itemId;
	private String category;
	private String label;
	private int occurrenceCount;
	private Instant lastSeenAt;
	private Instant updatedAt;

	// Scoring-engine state (common.scoring.ScoringState projected onto this row) - see
	// WeakPointScoringOrchestrator for how these are read/derived/persisted around each attempt.
	private double easeFactor;
	private double halfLifeDays;
	private double mastery;
	private int leitnerBox;
	private Instant nextReviewAt;
	private Double lastWeakScore;
	/** Normalized (trim/collapse-whitespace/lowercase) label, the key used for population-level difficulty stats. */
	private String labelKey;
}
