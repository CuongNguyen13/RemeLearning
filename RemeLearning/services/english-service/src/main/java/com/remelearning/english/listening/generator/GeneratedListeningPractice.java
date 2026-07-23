package com.remelearning.english.listening.generator;

import com.remelearning.english.learn.common.DialogueLine;
import com.remelearning.english.listening.domain.ListeningQuestionItem;

import java.util.List;

/** Result of {@link ListeningPracticeGenerator#generate} - not yet synthesized to audio. */
public record GeneratedListeningPractice(String topic, List<DialogueLine> lines, List<ListeningQuestionItem> questions) {
}
