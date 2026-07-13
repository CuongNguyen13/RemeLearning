package com.remelearning.vocabulary.service;

import com.remelearning.vocabulary.domain.VocabularyType;
import com.remelearning.vocabulary.domain.VocabularyWeakPoint;
import com.remelearning.vocabulary.event.LearningGapAnalyzedEvent;

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
}
