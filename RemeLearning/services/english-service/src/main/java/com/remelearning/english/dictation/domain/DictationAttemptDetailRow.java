package com.remelearning.english.dictation.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/** One attempt joined with its resolved reference text and taxonomy, for the History detail read. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DictationAttemptDetailRow {
	private Long attemptId;
	private Long clipId;
	private Long practiceItemId;
	private String title;
	private String skill;
	private String level;
	private String examType;
	private String referenceText;
	private String userTranscript;
	private double accuracy;
	private double wer;
	/** JSON-encoded array of suggestion strings; null for attempts made before this column existed. */
	private String aiSuggestions;
	private Instant createdAt;
}
