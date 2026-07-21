package com.remelearning.english.dictation.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * One sentence of a {@link DictationClip}'s script, ordered by {@code seq} within the clip.
 * {@code startMs}/{@code endMs} are null until the separate AI-alignment step (STT via ai-service)
 * has located this sentence in the audio timeline. {@code translation} is null until lazily
 * translated to the learner's UI language (see {@code DictationServiceImpl#getClipDetail}).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DictationClipSentence {
	private Long id;
	private Long clipId;
	private int seq;
	private String text;
	private Integer startMs;
	private Integer endMs;
	private String translation;
	private Instant createdAt;
}
