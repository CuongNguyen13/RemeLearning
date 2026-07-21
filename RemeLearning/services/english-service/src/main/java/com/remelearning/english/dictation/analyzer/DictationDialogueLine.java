package com.remelearning.english.dictation.analyzer;

/**
 * One line/turn within a generated listening-practice passage, spoken by {@code speaker}.
 * {@code translation} is null unless a translation language was requested at generation time.
 */
public record DictationDialogueLine(String speaker, String text, String translation) {
}
