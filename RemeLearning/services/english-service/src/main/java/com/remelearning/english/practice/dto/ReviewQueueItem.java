package com.remelearning.english.practice.dto;

import lombok.Builder;
import lombok.Getter;

import java.time.Instant;

/** One item due for review now, per the Leitner schedule computed by the Java scoring engine. */
@Getter
@Builder
public class ReviewQueueItem {
	private String itemId;
	private String category;
	private String label;
	private Double lastWeakScore;
	private Instant nextReviewAt;
}
