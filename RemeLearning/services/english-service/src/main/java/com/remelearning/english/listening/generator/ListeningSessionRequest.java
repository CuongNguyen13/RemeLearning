package com.remelearning.english.listening.generator;

import java.util.List;

/**
 * Everything {@link ListeningPracticeGenerator} needs to write one practice session's worth of
 * passages. Grouped into a record rather than passed as six positional parameters, and carrying
 * {@code avoidTopics}/{@code passageCount} that the old single-passage signature had no room for.
 *
 * @param targetKeywords  words/phrases to reuse across the session; may be empty, letting the
 *                        implementation pick its own level-appropriate topics
 * @param level           CEFR target (e.g. "B1"); null lets the implementation pick a default
 * @param examType        exam style to frame the passages around (e.g. "TOEIC"); may be null
 * @param translationLang UI language to also translate each line into; null/"en" means none
 * @param avoidTopics     topics the learner already practised, which the new passages must not
 *                        reuse - the main defence against every session coming back near-identical
 * @param passageCount    how many distinct passages this session should contain
 */
public record ListeningSessionRequest(
		List<String> targetKeywords,
		String level,
		String examType,
		String translationLang,
		List<String> avoidTopics,
		int passageCount) {
}
