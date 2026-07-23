package com.remelearning.english.speaking.dto;

import lombok.Builder;
import lombok.Getter;

import java.time.Instant;

/** Unlike vocabulary/grammar/listening, the target text is shown upfront - speaking practice is
 * about pronouncing known text, not testing comprehension. */
@Getter
@Builder
public class SpeakingPracticeItemDto {
	private Long practiceItemId;
	/** Null until Supertonic has synthesized the sample (model) audio. */
	private String sampleAudioUrl;
	private String level;
	private String examType;
	private String topic;
	private String targetText;
	private String translation;
	private Instant createdAt;
}
