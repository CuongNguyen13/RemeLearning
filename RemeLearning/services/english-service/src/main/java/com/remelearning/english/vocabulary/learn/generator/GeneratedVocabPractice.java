package com.remelearning.english.vocabulary.learn.generator;

import com.remelearning.english.vocabulary.learn.domain.VocabQuestionItem;

import java.util.List;

/** Result of {@link VocabPracticeGenerator#generate}. */
public record GeneratedVocabPractice(String topic, List<VocabQuestionItem> items) {
}
