package com.remelearning.english.dictation.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * One graded dictation attempt. Exactly one of {@code clipId} (a fixed-library clip) or
 * {@code practiceItemId} (an AI-practice clip) is set - both are graded by the same flow.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DictationAttempt {
	private Long id;
	private Long clipId;
	private Long practiceItemId;
	private String userId;
	private String userTranscript;
	private double accuracy;
	private double wer;
	private Instant createdAt;
}
