package com.remelearning.english.speaking.library.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * One row in {@code speaking_library_attempts} — a learner's scored attempt at one sentence (unlike
 * {@code ListeningLibraryAttempt}, which scores a whole section's answer set at once, speaking scores
 * one sentence per recorded attempt).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SpeakingLibraryAttempt {
	private Long id;
	private String userId;
	private Long sectionId;
	private Long sentenceId;
	private Double phonemeScore;
	private Double wordScore;
	private String recordedAudioStorageKey;
	private Instant createdAt;
}
