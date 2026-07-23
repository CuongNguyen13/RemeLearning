package com.remelearning.english.grammar.library.dto;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class FinishGrammarLibrarySessionResponse {
	private Long sessionId;
	private int correctCount;
	private int totalCount;
	private boolean passed;
	/** Non-null only when {@code passed} is false — a fresh RETRY session covering the missed questions. */
	private StartGrammarSessionResponse retrySession;
	private boolean nextTopicUnlocked;
	private Long nextTopicId;
}
