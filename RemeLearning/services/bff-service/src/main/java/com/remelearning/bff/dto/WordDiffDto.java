package com.remelearning.bff.dto;

import lombok.Data;

/** One aligned word slot in a graded dictation transcript diff, proxied from english-service. */
@Data
public class WordDiffDto {
	private String tag;
	private String actualWord;
	private String expectedWord;
}
