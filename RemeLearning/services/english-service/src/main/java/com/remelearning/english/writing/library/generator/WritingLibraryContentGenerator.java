package com.remelearning.english.writing.library.generator;

import com.remelearning.english.writing.domain.WritingTaskType;
import com.remelearning.english.writing.library.domain.WritingLibraryPrompt;
import com.remelearning.english.writing.library.domain.WritingLibraryTopic;

/**
 * Generates and persists one prompt for a library topic, on demand. Callers depend on this interface,
 * not the implementation, so the generation provider can change without touching them.
 */
public interface WritingLibraryContentGenerator {

	/**
	 * Generates one prompt for {@code topic} of the requested task type and inserts it into the
	 * topic's chain. Never returns null and never throws for an LLM/parse failure - it degrades to a
	 * template built from the topic itself, since a learner opening a topic must always get something
	 * to write.
	 *
	 * @param examType exam style being prepared for (see {@code ExamTypes}); decides the passage
	 *                 length and register. Null/blank/unrecognised means everyday English.
	 */
	WritingLibraryPrompt generatePrompt(WritingLibraryTopic topic, WritingTaskType taskType, String examType);
}
