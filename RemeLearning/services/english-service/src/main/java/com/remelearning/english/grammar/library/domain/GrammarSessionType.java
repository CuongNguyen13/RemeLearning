package com.remelearning.english.grammar.library.domain;

/**
 * {@code INITIAL} — the full 8-10 question pool for a topic, taken the first time (or any time
 * starting fresh). {@code RETRY} — a smaller, AI-regenerated set covering only the questions the
 * learner got wrong in the previous session, so a topic is only PASSED once every rule has been
 * answered correctly at least once.
 */
public enum GrammarSessionType {
	INITIAL,
	RETRY
}
