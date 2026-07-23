package com.remelearning.english.listening.dto;

import lombok.Builder;
import lombok.Getter;

import java.time.Instant;
import java.util.List;

/**
 * Practice-time shape - deliberately omits the transcript/translation (revealed only after
 * grading, in {@link ListeningAttemptResultDto}) so the learner can't just read the answer.
 */
@Getter
@Builder
public class ListeningPracticeItemDto {
	private Long practiceItemId;
	/** Null until Supertonic has finished synthesizing the audio. */
	private String audioUrl;
	private String level;
	private String examType;
	private String topic;
	private List<ListeningQuestionDto> questions;
	private Instant createdAt;
}
