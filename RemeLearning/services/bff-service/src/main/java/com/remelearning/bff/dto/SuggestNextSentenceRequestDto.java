package com.remelearning.bff.dto;

import lombok.Data;

/** Ask for hints on what to write next; draftText may be blank (help with the opening). */
@Data
public class SuggestNextSentenceRequestDto {
	private Long practiceItemId;
	private String draftText;
}
