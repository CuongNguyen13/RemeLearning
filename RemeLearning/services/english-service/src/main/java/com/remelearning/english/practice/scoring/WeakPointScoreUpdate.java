package com.remelearning.english.practice.scoring;

import lombok.Builder;
import lombok.Getter;

import java.time.Instant;

/**
 * A freshly Java-computed score for one item, ready to be pushed into whichever domain
 * ({@code vocabulary}/{@code grammar}/{@code pronunciation}) owns {@code category}, via that
 * domain's {@code applyJavaComputedScore}.
 */
@Getter
@Builder
public class WeakPointScoreUpdate {
	/** Synthetic id for this redo batch (no real recording is involved) - same convention as
	 *  {@code PracticeServiceImpl}'s existing {@code "practice-" + UUID} used for the Kafka event. */
	private String recordingId;
	private String userId;
	private String itemId;
	private String category;
	private String label;
	private double weakScore;
	private double masteryLevel;
	private Instant nextReviewAt;
}
