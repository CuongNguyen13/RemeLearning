package com.remelearning.english.vocabulary.library.generator;

import java.util.List;

/**
 * Generates new words to top up a topic's library word bank. Callers depend on this interface,
 * not the implementation, so the generation provider can change without touching them.
 */
public interface LibraryWordGenerator {

	/**
	 * Never throws - degrades to an empty list on any LLM/parse failure, since a flaky call here
	 * can't block a learner from starting a Section with whatever words already exist.
	 *
	 * @param topicName     the topic to generate words for (e.g. "Travel")
	 * @param existingWords words already in this topic's bank, to avoid repeating
	 * @param count         how many new words to request
	 */
	List<GeneratedLibraryWord> generate(String topicName, List<String> existingWords, int count);
}
