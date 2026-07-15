package com.remelearning.english.practice.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/** One graded answer inside a {@link PracticeRedoRequest} batch. */
@Data
public class PracticeAttemptRequest {
	@NotBlank
	private String itemId;

	/** "vocabulary", "grammar" or "pronunciation" — must match the item's original weak-point category. */
	@NotBlank
	private String category;

	@NotBlank
	private String label;

	private boolean correct;
}
