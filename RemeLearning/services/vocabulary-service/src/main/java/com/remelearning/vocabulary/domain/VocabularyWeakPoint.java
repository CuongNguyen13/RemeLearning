package com.remelearning.vocabulary.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * A recurring/forgotten vocabulary item for a learner, derived from ai-service's
 * {@code learning.gap.analyzed} event (filtered to category = "vocabulary") and
 * classified by {@link com.remelearning.vocabulary.classifier.VocabularyClassifier}.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VocabularyWeakPoint {
	private Long id;
	private String recordingId;
	private String userId;
	private String itemId;
	private String label;
	private VocabularyType vocabularyType;
	private double forgettingScore;
	private String recommendation;
	private Instant updatedAt;
}
