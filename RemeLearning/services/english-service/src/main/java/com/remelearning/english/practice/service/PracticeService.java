package com.remelearning.english.practice.service;

import com.remelearning.english.practice.dto.PracticeRedoRequest;
import com.remelearning.english.practice.dto.ReviewQueueItem;

import java.util.List;

public interface PracticeService {

	/**
	 * Grades and persists every attempt in a redo-exercise submission, refreshes the learner's
	 * mistake history, and requests ai-service re-score it via {@code learning.gap.analysis.requested}.
	 */
	void redo(PracticeRedoRequest request);

	/** Items due for review now or earlier, per the Leitner schedule, soonest-first. */
	List<ReviewQueueItem> getReviewQueue(String userId);
}
