package com.remelearning.english.dictation.analyzer;

import java.util.List;

/**
 * Translates a library clip's sentences into a target UI language, for the lazy per-sentence
 * translation shown alongside the dictation hint. Callers depend on this interface, not the
 * implementation, so the translation provider can change without touching them.
 */
public interface DictationSentenceTranslator {

	/**
	 * Never throws - on any failure (LLM error, unparsable/mismatched response), returns a list the
	 * same size as {@code sentences} filled with nulls, so callers can always zip the result 1:1
	 * against their input and simply skip whichever entries came back null.
	 */
	List<String> translate(List<String> sentences, String targetLang);
}
