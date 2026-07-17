package com.remelearning.common.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Mirrors ai-service's {@code app.schemas.events.MistakeHistoryItem} — one recurring mistake
 * item as tracked by a backend service (grammar/vocabulary/pronunciation), bundled into an
 * {@link AnalysisRequestedEvent} so ai-service can recompute its forgetting score.
 *
 * <p>The scoring-state fields ({@code halfLifeDays}..{@code incorrectCount}) let ai-service's
 * {@code ScoringEngineAnalyzer} reproduce the same composite score the Java-direct redo path
 * ({@code common.scoring.WeakPointScoringEngine}) computes, so both flows stay consistent. They're
 * projected from the producer's {@code mistake_history} row + population difficulty stats; the
 * legacy {@code RuleBasedAnalyzer} ignores them.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MistakeHistoryItemPayload {
	private String itemId;
	private String category;
	private String label;
	private int occurrenceCount;
	private double lastSeenDaysAgo;
	private double halfLifeDays;
	private double easeFactor;
	private double mastery;
	private int leitnerBox;
	private long correctCount;
	private long incorrectCount;
}
