package com.remelearning.english.listening.library.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/** One row in {@code listening_library_attempts} — a learner's completed attempt at one section. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ListeningLibraryAttempt {
	private Long id;
	private String userId;
	private Long sectionId;
	private Double score;
	private Integer correctCount;
	private Integer totalQuestions;
	private Instant startedAt;
	private Instant completedAt;
}
