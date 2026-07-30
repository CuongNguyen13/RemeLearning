package com.remelearning.english.writing.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * Ask for hints on what to write next. {@code draftText} may be blank - that means the learner
 * hasn't started yet and wants help with the opening.
 */
@Data
public class SuggestNextSentenceRequest {

	@NotNull
	private Long practiceItemId;

	private String draftText;
}
