package com.remelearning.english.listening.library.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * One row in {@code listening_library_sections} — one passage (with optional audio) belonging to a
 * {@link ListeningLibraryTopic}, backed by its own reusable multiple-choice question pool.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ListeningLibrarySection {
	private Long id;
	private Long topicId;
	private String passageText;
	/** S3 storage key for the narrated audio; null if no audio has been generated/uploaded yet. */
	private String audioStorageKey;
	private Instant createdAt;
}
