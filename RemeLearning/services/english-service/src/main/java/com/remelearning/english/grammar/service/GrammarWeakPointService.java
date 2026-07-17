package com.remelearning.english.grammar.service;

import com.remelearning.english.grammar.domain.GrammarType;
import com.remelearning.english.grammar.domain.GrammarWeakPoint;
import com.remelearning.common.event.LearningGapAnalyzedEvent;
import com.remelearning.english.practice.scoring.WeakPointScoreUpdate;

import java.util.List;

/**
 * Persists and reads back a learner's grammar weak points derived from {@code learning.gap.analyzed}.
 * Callers (controller/Kafka consumer) depend on this interface, not
 * {@link GrammarWeakPointServiceImpl}, so the classifier/persistence strategy can change later
 * without touching them.
 */
public interface GrammarWeakPointService {

	void saveWeakPoints(LearningGapAnalyzedEvent event);

	List<GrammarWeakPoint> getWeakPoints(String userId, GrammarType type);

	/** The learner's {@code limit} most-forgotten grammar rules, for the dictation feature. */
	List<GrammarWeakPoint> getTopWeakPoints(String userId, int limit);

	/**
	 * Persists a score computed directly by the practice/redo flow's Java scoring engine, bypassing
	 * the ai-service/Kafka round-trip. No-op for updates whose category isn't "grammar".
	 */
	void applyJavaComputedScore(WeakPointScoreUpdate update);
}
