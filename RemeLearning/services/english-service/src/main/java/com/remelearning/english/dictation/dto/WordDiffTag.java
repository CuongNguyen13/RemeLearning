package com.remelearning.english.dictation.dto;

/** How one word in a graded transcript compares to the reference sentence, per {@link WordDiffDto}. */
public enum WordDiffTag {
	/** The learner's word matches the reference word at this position. */
	CORRECT,
	/** The learner typed a different word than the reference at this position. */
	SUBSTITUTED,
	/** A reference word the learner's transcript is missing entirely. */
	MISSING,
	/** A word the learner typed that has no counterpart in the reference. */
	EXTRA
}
