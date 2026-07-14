package com.remelearning.english.pronunciation.service;

import com.remelearning.english.pronunciation.domain.PronunciationType;
import com.remelearning.english.pronunciation.domain.PronunciationWeakPoint;
import com.remelearning.english.pronunciation.event.LearningGapAnalyzedEvent;

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
}
