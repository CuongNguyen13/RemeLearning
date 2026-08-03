package com.remelearning.english.learn.common;

/**
 * A passage's lines rendered into the two flat strings every "learn" skill persists next to the
 * audio: the transcript and (when the generator was asked for one) its translation.
 *
 * @param transcriptText  every line joined by newlines, speaker-prefixed only for multi-speaker
 *                        passages
 * @param translationText the same shape for the translated lines, or null when no line carried a
 *                        translation
 */
public record DialogueText(String transcriptText, String translationText) {
}
