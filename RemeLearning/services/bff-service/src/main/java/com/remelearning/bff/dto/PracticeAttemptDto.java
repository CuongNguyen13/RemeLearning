package com.remelearning.bff.dto;

import lombok.Data;

/** One graded answer inside a {@link PracticeRedoRequestDto}; mirrors english-service's PracticeAttemptRequest. */
@Data
public class PracticeAttemptDto {
	private String itemId;
	private String category;
	private String label;
	private boolean correct;
}
