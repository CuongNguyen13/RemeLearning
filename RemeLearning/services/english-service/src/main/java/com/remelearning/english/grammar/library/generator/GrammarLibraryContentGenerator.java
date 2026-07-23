package com.remelearning.english.grammar.library.generator;

import com.remelearning.english.grammar.learn.domain.GrammarQuestionType;

/** Generates AI content for the Grammar Library skill; see {@link LlmGrammarLibraryContentGenerator} for the only implementation. */
public interface GrammarLibraryContentGenerator {

	/** Generates a topic's theory page (bilingual explanation + illustration + examples) plus an 8-10 item question pool. */
	GeneratedGrammarTopicContent generateTopicContent(String topicName, String level);

	/** Generates one replacement question of the same type, covering the same topic, for a RETRY session. */
	GrammarLibraryQuestionSeed generateRetryQuestion(String topicName, String level, GrammarQuestionType questionType, String avoidPrompt);
}
