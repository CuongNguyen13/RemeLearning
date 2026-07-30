package com.remelearning.english.listening.weakpoint.domain;

import com.remelearning.common.scoring.ScoreSource;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * A recurring/forgotten listening weak point for a learner, derived either from dictation's
 * dual-write onto {@code learning.gap.analyzed} (category = "listening", {@code sourceType =
 * DICTATION}, {@code scoreSource = PYTHON_LEGACY}) or directly from the listening-comprehension
 * practice/redo flow's Java scoring engine ({@code sourceType = COMPREHENSION}, {@code scoreSource =
 * JAVA_ENGINE}).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ListeningWeakPoint {
	private Long id;
	private String recordingId;
	private String userId;
	private String itemId;
	private String label;
	private ListeningSourceType sourceType;
	private double forgettingScore;
	private String recommendation;
	private Instant updatedAt;
	private Double masteryLevel;
	private Instant nextReviewAt;
	private ScoreSource scoreSource;
}
