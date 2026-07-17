package com.remelearning.english.dictation.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * A Gemini-suggested practice sentence for the "Luyện nghe với AI" section, generated from a
 * learner's recurring dictation misses. {@code storageKey} is null until Supertonic TTS synthesizes
 * its audio (see {@code DictationService.generateAiPractice}).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DictationPracticeItem {
	private Long id;
	private String userId;
	private String sentenceText;
	private String source;
	private String storageKey;
	private Instant createdAt;
}
