package com.remelearning.common.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Mirrors ai-service's {@code app.schemas.events.MistakeHistoryItem} — one recurring mistake
 * item as tracked by a backend service (grammar/vocabulary/pronunciation), bundled into an
 * {@link AnalysisRequestedEvent} so ai-service's {@code RuleBasedAnalyzer} can recompute its
 * forgetting score.
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
}
