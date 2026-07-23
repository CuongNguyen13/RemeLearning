package com.remelearning.common.ai.pronunciation;

import java.util.List;

/** Result of {@link PronunciationScoringClient#score} - mirrors ai-service's PronunciationScoreResponse. */
public record PronunciationScore(double overall, List<WordPronunciationScore> words, String transcript, List<String> weakPhonemes) {
}
