package com.remelearning.english.pronunciation.service;

import com.remelearning.english.pronunciation.domain.PronunciationType;
import com.remelearning.english.pronunciation.domain.PronunciationWeakPoint;
import com.remelearning.common.event.LearningGapAnalyzedEvent;
import com.remelearning.english.practice.scoring.WeakPointScoreUpdate;

import java.util.List;

/**
 * Persists and reads back a learner's pronunciation weak points derived from
 * {@code learning.gap.analyzed}. Callers (controller/Kafka consumer) depend on this interface,
 * not {@link PronunciationWeakPointServiceImpl}, so the classifier/persistence strategy can
 * change later without touching them.
 */
public interface PronunciationWeakPointService {

	void saveWeakPoints(LearningGapAnalyzedEvent event);

	List<PronunciationWeakPoint> getWeakPoints(String userId, PronunciationType type);

	/**
	 * Persists a score computed directly by the practice/redo flow's Java scoring engine, bypassing
	 * the ai-service/Kafka round-trip. No-op for updates whose category isn't "pronunciation".
	 */
	void applyJavaComputedScore(WeakPointScoreUpdate update);
}
