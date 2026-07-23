package com.remelearning.english.grammar.library.domain;

/** Lifecycle of one {@link GrammarLibrarySession}: {@code IN_PROGRESS} while answers are being submitted, {@code COMPLETED} once finished. */
public enum GrammarSessionStatus {
	IN_PROGRESS,
	COMPLETED
}
