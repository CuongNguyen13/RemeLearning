package com.remelearning.bff.dto;

import lombok.Data;

/** One incorrect sentence-mode attempt, proxied straight through to english-service. */
@Data
public class DictationSentenceMistakeDto {
	private Integer sentenceIndex;
	private String expectedText;
	private String attemptedText;
}
