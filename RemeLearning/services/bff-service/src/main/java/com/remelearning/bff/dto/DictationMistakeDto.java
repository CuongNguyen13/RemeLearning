package com.remelearning.bff.dto;

import lombok.Data;

/** One word the learner got wrong in a past attempt, proxied from english-service. */
@Data
public class DictationMistakeDto {
	private String expectedWord;
	private String actualWord;
	private String tag;
}
