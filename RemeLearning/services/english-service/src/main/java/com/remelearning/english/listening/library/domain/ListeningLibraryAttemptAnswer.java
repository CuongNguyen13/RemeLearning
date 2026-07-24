package com.remelearning.english.listening.library.domain;

import lombok.Data;

import java.time.OffsetDateTime;

/**
 * One row in {@code listening_library_attempt_answers} — records exactly which option a learner
 * picked for one question within one attempt, so a later feature can regenerate AI practice
 * targeting only the questions actually missed (mirrors dictation's mistake-history pattern).
 */
@Data
public class ListeningLibraryAttemptAnswer {
	private Long id;
	private Long attemptId;
	private Long questionId;
	private String selectedOption;
	private String correctOption;
	private Boolean isCorrect;
	private OffsetDateTime createdAt;
}
