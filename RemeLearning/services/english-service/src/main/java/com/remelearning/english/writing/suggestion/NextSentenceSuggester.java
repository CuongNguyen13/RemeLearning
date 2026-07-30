package com.remelearning.english.writing.suggestion;

import com.remelearning.english.writing.domain.WritingSuggestion;
import com.remelearning.english.writing.domain.WritingTaskType;

import java.util.List;

/**
 * Suggests what the learner could write next, given what they have drafted so far. Callers depend on
 * this interface, not the implementation, so the provider can change without touching them.
 */
public interface NextSentenceSuggester {

	/**
	 * Never throws - returns an empty list on any LLM/parse failure, letting the UI simply show
	 * "chưa có gợi ý" rather than failing the learner's writing session.
	 *
	 * <p>Implementations must never receive or reveal the prompt's reference answer for a
	 * translation task: handing back the model translation would replace the exercise instead of
	 * scaffolding it.
	 *
	 * @param draftText what the learner has written so far; may be blank (help with the opening)
	 */
	List<WritingSuggestion> suggest(WritingTaskType taskType, String promptText, String draftText, String level);
}
