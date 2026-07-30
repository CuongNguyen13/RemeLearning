package com.remelearning.english.writing.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/** A learner's finished text, submitted for AI grading. */
@Data
public class SubmitWritingAttemptRequest {

	@NotBlank
	private String userId;

	@NotNull
	private Long practiceItemId;

	@NotBlank
	private String submittedText;
}
