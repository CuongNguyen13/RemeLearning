package com.remelearning.english.writing.generator;

import com.remelearning.english.writing.domain.WritingTaskType;

import java.util.List;

/**
 * Generates one writing brief or translation passage, reusing a learner's target weak-point labels
 * when given. Callers depend on this interface, not the implementation, so the generation provider
 * can change without touching them.
 */
public interface WritingPracticeGenerator {

	/**
	 * Never returns null and never throws - degrades to a static template on any LLM/parse failure.
	 *
	 * @param taskType     which of the three modes to generate for; decides what {@code promptText}
	 *                     and {@code referenceAnswer} mean
	 * @param targetLabels grammar/vocabulary weak-point labels to build the prompt around; may be
	 *                     empty, letting the implementation pick its own level-appropriate topic
	 * @param level        CEFR target (e.g. "B1"); null lets the implementation pick a default
	 * @param examType     exam style to frame the task around (e.g. "IELTS"); may be null
	 */
	GeneratedWritingPractice generate(WritingTaskType taskType, List<String> targetLabels, String level, String examType);
}
