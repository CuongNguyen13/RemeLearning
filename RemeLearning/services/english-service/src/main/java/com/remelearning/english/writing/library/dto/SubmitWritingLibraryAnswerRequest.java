package com.remelearning.english.writing.library.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/** A learner's finished text for one library prompt, submitted for AI grading. */
@Data
public class SubmitWritingLibraryAnswerRequest {

	@NotBlank
	private String submittedText;
}
