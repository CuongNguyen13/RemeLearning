package com.remelearning.bff.dto;

import lombok.Data;

/**
 * Result of finishing a grammar-library session: PASSED + next-topic unlock when all correct,
 * otherwise a fresh RETRY session covering the missed questions.
 */
@Data
public class FinishGrammarLibrarySessionResponseDto {
	private Long sessionId;
	private int correctCount;
	private int totalCount;
	private boolean passed;
	private StartGrammarSessionResponseDto retrySession;
	private boolean nextTopicUnlocked;
	private Long nextTopicId;
}
