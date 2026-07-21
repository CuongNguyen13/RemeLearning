package com.remelearning.common.ai.align;

/**
 * One sentence's audio timestamps as located by a {@link SentenceAlignmentClient}, in the same
 * order as the sentence list passed to {@code align}. Both fields are null when the sentence
 * couldn't be located in the audio - callers should leave it unaligned rather than guessing.
 */
public record SentenceTiming(Integer startMs, Integer endMs) {
}
