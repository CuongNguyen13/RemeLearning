package com.remelearning.english.grammar.learn.generator;

import com.remelearning.english.grammar.learn.domain.GrammarQuestionItem;

import java.util.List;

/** Result of {@link GrammarPracticeGenerator#generate}. */
public record GeneratedGrammarPractice(String topic, List<GrammarQuestionItem> items) {
}
