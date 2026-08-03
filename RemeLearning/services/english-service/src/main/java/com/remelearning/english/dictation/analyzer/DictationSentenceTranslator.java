package com.remelearning.english.dictation.analyzer;

import java.util.List;

/**
 * Translates a library clip's sentences into a target UI language, for the lazy per-sentence
 * translation shown alongside the dictation hint. Callers depend on this interface, not the
 * implementation, so the translation provider can change without touching them.
 */
public interface DictationSentenceTranslator {

	/**
	 * Returns a list the same size as {@code sentences}, in the same order, so callers can zip the
	 * result 1:1 against their input. Any failure (LLM error, unparsable or count-mismatched
	 * response) propagates as an {@code AiContentException} instead of returning null entries.
	 */
	List<String> translate(List<String> sentences, String targetLang);
}
