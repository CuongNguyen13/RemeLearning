package com.remelearning.common.scoring;

/**
 * Which side computed a weak-point row's current score - lets a domain's upsert refuse to let a
 * stale {@code PYTHON_LEGACY} recompute (from the existing {@code learning.gap.analyzed} Kafka
 * flow) silently overwrite a fresher {@code JAVA_ENGINE} score (from the practice/redo flow). A
 * one-way ratchet: once a row is JAVA_ENGINE-owned, only another JAVA_ENGINE write can update it.
 */
public enum ScoreSource {
	PYTHON_LEGACY,
	JAVA_ENGINE
}
