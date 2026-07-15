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
}
