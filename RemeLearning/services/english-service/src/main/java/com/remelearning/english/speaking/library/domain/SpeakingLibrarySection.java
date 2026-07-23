package com.remelearning.english.speaking.library.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * One row in {@code speaking_library_sections} — one section belonging to a
 * {@link SpeakingLibraryTopic}, backed by its own reusable sample-sentence pool (unlike
 * {@code ListeningLibrarySection}, a section here carries no passage/audio of its own — each
 * sentence has its own sample audio instead).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SpeakingLibrarySection {
	private Long id;
	private Long topicId;
	private Instant createdAt;
}
