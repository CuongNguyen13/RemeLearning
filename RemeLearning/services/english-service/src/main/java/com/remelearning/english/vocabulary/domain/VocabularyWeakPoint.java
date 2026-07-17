package com.remelearning.english.vocabulary.domain;

import com.remelearning.common.scoring.ScoreSource;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * A recurring/forgotten vocabulary item for a learner, derived either from ai-service's
 * {@code learning.gap.analyzed} event (filtered to category = "vocabulary", {@code scoreSource =
 * PYTHON_LEGACY}) or directly from the practice/redo flow's Java scoring engine ({@code
 * scoreSource = JAVA_ENGINE}), and classified by
 * {@link com.remelearning.english.vocabulary.classifier.VocabularyClassifier}.
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
	/** Null until the Java engine has scored this item at least once. */
	private Double masteryLevel;
	private Instant nextReviewAt;
	private ScoreSource scoreSource;
}
