package com.remelearning.english.dictation.analyzer;

import java.util.List;

/**
 * Generates one listening-practice passage - a monologue or a multi-speaker dialogue - that
 * naturally reuses a learner's hard-to-hear words/phrases, for the "Luyện nghe với AI" section.
 * Callers depend on this interface, not the implementation, so the generation provider can change
 * without touching them.
 */
public interface DictationDialogueGenerator {

	/**
	 * Never returns a result with null/empty lines and never throws - implementations must degrade to
	 * one templated line per phrase (see {@link DictationAnalysisTemplates}) with a null topic on any
	 * LLM/parse failure.
	 *
	 * @param targetPhrases the words/phrases to practice; may be empty for a generic passage
	 * @param level CEFR target (e.g. "B1"); null lets the implementation pick a sensible default
	 * @param examType exam style to frame the passage around (e.g. "TOEIC"); null for no framing
	 * @param translationLang UI language to also translate each line into; null or "en" (the content's
	 *                        own language) means no translation is requested
	 */
	DialogueGenerationResult generateDialogue(List<String> targetPhrases, String level, String examType, String translationLang);
}
