package com.remelearning.english.pronunciation.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * A recurring/forgotten pronunciation issue for a learner, derived from ai-service's
 * {@code learning.gap.analyzed} event (filtered to category = "pronunciation") and
 * classified by {@link com.remelearning.english.pronunciation.classifier.PronunciationClassifier}.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PronunciationWeakPoint {
	private Long id;
	private String recordingId;
	private String userId;
	private String itemId;
	private String label;
	private PronunciationType pronunciationType;
	private double forgettingScore;
	private String recommendation;
	private Instant updatedAt;
}
