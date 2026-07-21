package com.remelearning.english.dictation.analyzer;

/** Root-cause classification for one dictation mistake, used by {@link DictationAnalysis}. */
public enum DictationErrorCategory {
	/** Vocabulary: homophone confusion, unknown collocation/idiom, slang/dialect. */
	LEXICON,
	/** Grammar/morphology: dropped inflection (s/ed/ing), wrong tense, unrecognized structure. */
	GRAMMAR,
	/** Phonology/connected speech: elision, linking, assimilation, weak forms of function words. */
	PHONOLOGY
}
