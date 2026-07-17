package com.remelearning.english.vocabulary.service;

import com.remelearning.english.vocabulary.domain.VocabularyType;
import com.remelearning.english.vocabulary.domain.VocabularyWeakPoint;
import com.remelearning.common.event.LearningGapAnalyzedEvent;
import com.remelearning.english.practice.scoring.WeakPointScoreUpdate;

import java.util.List;

/**
 * Persists and reads back a learner's vocabulary weak points derived from {@code learning.gap.analyzed}.
 * Callers (controller/Kafka consumer) depend on this interface, not
 * {@link VocabularyWeakPointServiceImpl}, so the classifier/persistence strategy can change later
 * without touching them.
 */
public interface VocabularyWeakPointService {

	void saveWeakPoints(LearningGapAnalyzedEvent event);

	List<VocabularyWeakPoint> getWeakPoints(String userId, VocabularyType type);

	/** The learner's {@code limit} most-forgotten vocabulary items, for the dictation feature. */
	List<VocabularyWeakPoint> getTopWeakPoints(String userId, int limit);

	/**
	 * Persists a score computed directly by the practice/redo flow's Java scoring engine, bypassing
	 * the ai-service/Kafka round-trip. No-op for updates whose category isn't "vocabulary".
	 */
	void applyJavaComputedScore(WeakPointScoreUpdate update);
}
