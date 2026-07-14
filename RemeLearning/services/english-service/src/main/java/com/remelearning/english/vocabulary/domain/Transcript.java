package com.remelearning.english.vocabulary.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/** Persisted transcript for one recording, produced by ai-service's STT+diarization pipeline. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Transcript {
	private Long id;
	private String recordingId;
	private String userId;
	private String fullText;
	private Instant createdAt;
}
