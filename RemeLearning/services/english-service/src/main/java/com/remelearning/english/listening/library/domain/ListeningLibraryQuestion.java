package com.remelearning.english.listening.library.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * One row in {@code listening_library_questions} — a multiple-choice comprehension question tied
 * to one {@link ListeningLibrarySection}'s passage/audio.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ListeningLibraryQuestion {
	private Long id;
	private Long sectionId;
	private String questionText;
	/** JSON array of the multiple-choice options. */
	private String optionsJson;
	private String correctOption;
	private String explanation;
	private Instant createdAt;
}
