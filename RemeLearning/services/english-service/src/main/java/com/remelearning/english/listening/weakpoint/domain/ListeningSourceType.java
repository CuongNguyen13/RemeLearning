package com.remelearning.english.listening.weakpoint.domain;

/**
 * Which of the two listening-skill flows produced a {@link ListeningWeakPoint}: a mistyped word in
 * a dictation attempt ({@code DICTATION}, dual-written alongside its own root-cause category), or a
 * missed question in a listening-comprehension redo ({@code COMPREHENSION}, scored by the practice/
 * redo flow's Java engine).
 */
public enum ListeningSourceType {
	DICTATION,
	COMPREHENSION
}
