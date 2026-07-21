package com.remelearning.english.dictation.analyzer;

import java.util.List;

/** One generated listening-practice passage: its topic label (may be null on fallback) plus its lines. */
public record DialogueGenerationResult(String topic, List<DictationDialogueLine> lines) {
}
