package com.remelearning.common.scoring;

/**
 * Normalizes a weak-point label into the key used for population-level difficulty stats
 * ({@link RaschDifficultyEstimator}). Computed once here (not re-derived via SQL {@code LOWER()}
 * in some places and Java in others) so a label written by one caller reliably matches the same
 * label read by another.
 */
public final class LabelKeys {

	private LabelKeys() {
	}

	/** Trims, collapses internal whitespace, and lowercases - does not strip any "category: term" prefix. */
	public static String normalize(String label) {
		if (label == null) {
			return "";
		}
		return label.trim().replaceAll("\\s+", " ").toLowerCase();
	}
}
