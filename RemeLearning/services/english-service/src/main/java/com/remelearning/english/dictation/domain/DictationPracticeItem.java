package com.remelearning.english.dictation.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * A Gemini-suggested practice sentence/dialogue for the "Luyện nghe với AI" section, generated from
 * a learner's recurring dictation misses. {@code storageKey} is null until Supertonic TTS synthesizes
 * its audio. {@code level}/{@code examType}/{@code topic} mirror {@link DictationClip}'s taxonomy and
 * are null for single-sentence items (no generation-time facet selection); {@code translationText} is
 * the passage translated to the learner's UI language, newline-joined in the same line order as
 * {@code sentenceText}, or null when no translation was requested/generated.
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
	private String level;
	private String examType;
	private String topic;
	private String translationText;
	private Instant createdAt;
}
