package com.remelearning.english.pronunciation.domain;

import com.remelearning.common.scoring.ScoreSource;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * A recurring/forgotten pronunciation issue for a learner, derived either from ai-service's
 * {@code learning.gap.analyzed} event (filtered to category = "pronunciation", {@code scoreSource
 * = PYTHON_LEGACY}) or directly from the practice/redo flow's Java scoring engine ({@code
 * scoreSource = JAVA_ENGINE}), and classified by
 * {@link com.remelearning.english.pronunciation.classifier.PronunciationClassifier}.
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
	private Double masteryLevel;
	private Instant nextReviewAt;
	private ScoreSource scoreSource;
}
