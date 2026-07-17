package com.remelearning.english.dictation.dto;

import lombok.Builder;
import lombok.Getter;

/** One aligned word slot in a graded transcript diff, produced by the WER scorer. */
@Getter
@Builder
public class WordDiffDto {
	private WordDiffTag tag;
	/** The learner's word for this slot; null for a MISSING slot. */
	private String actualWord;
	/** The reference sentence's word for this slot; null for an EXTRA slot. */
	private String expectedWord;
}
