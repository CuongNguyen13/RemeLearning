package com.remelearning.english.listening.weakpoint.service;

import com.remelearning.common.event.LearningGapAnalyzedEvent;
import com.remelearning.english.listening.weakpoint.domain.ListeningSourceType;
import com.remelearning.english.listening.weakpoint.domain.ListeningWeakPoint;
import com.remelearning.english.practice.scoring.WeakPointScoreUpdate;

import java.util.List;

/**
 * Persists and reads back a learner's listening weak points derived from {@code
 * learning.gap.analyzed} (dictation dual-write) and from the practice/redo flow's Java-computed
 * listening-comprehension scores. Callers (controller/Kafka consumer/dispatcher) depend on this
 * interface, not {@link ListeningWeakPointServiceImpl}, so the persistence strategy can change
 * later without touching them.
 */
public interface ListeningWeakPointService {

	void saveWeakPoints(LearningGapAnalyzedEvent event);

	List<ListeningWeakPoint> getWeakPoints(String userId, ListeningSourceType sourceType);

	/**
	 * Persists a score computed directly by the practice/redo flow's Java scoring engine, bypassing
	 * the ai-service/Kafka round-trip. No-op for updates whose category isn't "listening".
	 */
	void applyJavaComputedScore(WeakPointScoreUpdate update);
}
