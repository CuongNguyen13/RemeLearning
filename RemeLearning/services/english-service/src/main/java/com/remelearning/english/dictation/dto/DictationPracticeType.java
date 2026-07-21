package com.remelearning.english.dictation.dto;

/** Distinguishes a dictation history entry's origin: a fixed real-audio library clip, or an
 * AI-generated ("Luyện nghe với AI") practice sentence. */
public enum DictationPracticeType {
	LIBRARY,
	AI_PRACTICE
}
