package com.remelearning.english.practice.service;

import com.remelearning.english.practice.dto.PracticeRedoRequest;

public interface PracticeService {

	/**
	 * Grades and persists every attempt in a redo-exercise submission, refreshes the learner's
	 * mistake history, and requests ai-service re-score it via {@code learning.gap.analysis.requested}.
	 */
	void redo(PracticeRedoRequest request);
}
