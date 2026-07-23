package com.remelearning.common.ai.pronunciation;

/** One scored phoneme (IPA symbol) within a word. */
public record PhonemePronunciationScore(String ipa, double score) {
}
