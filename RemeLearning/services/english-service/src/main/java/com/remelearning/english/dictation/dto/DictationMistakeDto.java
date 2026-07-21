package com.remelearning.english.dictation.dto;

import lombok.Builder;
import lombok.Getter;

/** One word the learner got wrong in a past attempt, for GET /api/v1/dictation/history/{userId}/{attemptId}. */
@Getter
@Builder
public class DictationMistakeDto {
	private String expectedWord;
	private String actualWord;
	private WordDiffTag tag;
}
