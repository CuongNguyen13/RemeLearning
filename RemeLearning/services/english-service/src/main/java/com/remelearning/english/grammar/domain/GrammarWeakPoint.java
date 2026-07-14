package com.remelearning.english.grammar.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * A recurring/forgotten grammar rule for a learner, derived from ai-service's
 * {@code learning.gap.analyzed} event (filtered to category = "grammar") and
 * classified by {@link com.remelearning.english.grammar.classifier.GrammarClassifier}.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GrammarWeakPoint {
	private Long id;
	private String recordingId;
	private String userId;
	private String itemId;
	private String label;
	private GrammarType grammarType;
	private double forgettingScore;
	private String recommendation;
	private Instant updatedAt;
}
