package com.remelearning.english.dictation.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/** One attempt joined with its clip's taxonomy plus the learner's running attempt count for that clip,
 * for a learner's dictation history - shows how many times they've practiced each lesson. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DictationHistoryRow {
	private Long attemptId;
	private Long clipId;
	private String title;
	private String skill;
	private String level;
	private String examType;
	private double accuracy;
	private double wer;
	private Instant createdAt;
	/** How many attempts (including this one) the learner has made on this clip; null for AI-practice entries. */
	private Integer attemptCount;
}
