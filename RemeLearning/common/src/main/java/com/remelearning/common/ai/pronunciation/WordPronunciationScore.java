package com.remelearning.common.ai.pronunciation;

import java.util.List;

/** One scored word, its overall score plus the per-phoneme breakdown. */
public record WordPronunciationScore(String word, double score, List<PhonemePronunciationScore> phonemes) {
}
