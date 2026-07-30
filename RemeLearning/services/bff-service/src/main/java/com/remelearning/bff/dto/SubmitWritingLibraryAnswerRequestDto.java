package com.remelearning.bff.dto;

import lombok.Data;

/** A learner's finished text for one library prompt, proxied to english-service for grading. */
@Data
public class SubmitWritingLibraryAnswerRequestDto {
	private String submittedText;
}
