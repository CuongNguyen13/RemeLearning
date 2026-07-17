package com.remelearning.english.dictation.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * One word a learner failed to transcribe correctly in an attempt (a MISSING or SUBSTITUTED slot
 * from the WER diff). The running ledger of these per (userId, expectedWord) is what the AI analysis
 * and the forgetting-score in {@code learning.gap.analyzed} are computed from.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DictationMiss {
	private Long id;
	private Long attemptId;
	private String userId;
	private Long clipId;
	private String expectedWord;
	private String actualWord;
	private String tag;
	private Instant createdAt;
}
