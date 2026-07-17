package com.remelearning.english.practice.scoring;

import com.remelearning.english.practice.dto.PracticeAttemptRequest;

/**
 * Grades one redo-exercise attempt directly against the Java scoring engine (common.scoring) and
 * pushes the result straight into the owning domain (vocabulary/grammar/pronunciation) service -
 * this is what lets the practice/redo flow compute a fresh weak-point score without waiting on
 * ai-service's Kafka round-trip.
 */
public interface WeakPointScoringOrchestrator {

	/**
	 * @param recurredInBatch true when this same item already appeared earlier in the same
	 *                        redo() batch - the caller tracks this across its own attempt loop.
	 */
	void scoreAttempt(String userId, String recordingId, PracticeAttemptRequest attempt, boolean recurredInBatch);
}
