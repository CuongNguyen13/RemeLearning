package com.remelearning.english.practice.scoring;

/** Routes a Java-computed weak-point score to whichever domain service owns its category. */
public interface WeakPointDispatcher {

	/** No-op (with a warning logged) if the category doesn't match any known domain. */
	void dispatch(WeakPointScoreUpdate update);
}
