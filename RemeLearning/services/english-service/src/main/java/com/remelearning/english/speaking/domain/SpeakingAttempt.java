package com.remelearning.english.speaking.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/** One graded attempt at a {@link SpeakingPracticeItem} (row in {@code speaking_attempts}). */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SpeakingAttempt {
	private Long id;
	private Long practiceItemId;
	private String userId;
	private String audioStorageKey;
	private double overallScore;
	/** JSON array of per-word (and per-phoneme) GOP scores - see PronunciationScore. */
	private String wordScoresJson;
	private String transcript;
	/** JSON array of the weak (low-scoring) ARPAbet/IPA phonemes flagged by ai-service. */
	private String weakPhonemesJson;
	private Instant createdAt;
}
