package com.remelearning.english.learn.common;

/**
 * One line of a generated listening passage (monologue or multi-speaker dialogue), the input unit
 * for {@link DialogueAudioSynthesizer}. {@code speaker} is any stable label ("A"/"B", a name, or a
 * single constant for a monologue); {@code translation} may be null when no translation was requested.
 */
public record DialogueLine(String speaker, String text, String translation) {
}
