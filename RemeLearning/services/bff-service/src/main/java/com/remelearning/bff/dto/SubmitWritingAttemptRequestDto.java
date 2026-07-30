package com.remelearning.bff.dto;

import lombok.Data;

/** A learner's finished text, proxied to english-service for grading. */
@Data
public class SubmitWritingAttemptRequestDto {
	/** Filled in by the controller from the path variable, never trusted from the body. */
	private String userId;
	private Long practiceItemId;
	private String submittedText;
}
