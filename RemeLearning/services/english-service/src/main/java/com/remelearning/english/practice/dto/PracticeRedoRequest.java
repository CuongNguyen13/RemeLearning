package com.remelearning.english.practice.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.util.List;

/** Payload for POST /api/v1/practice/redo — the graded results of a learner redoing an exercise. */
@Data
public class PracticeRedoRequest {
	@NotBlank
	private String userId;

	@NotEmpty
	private List<@Valid PracticeAttemptRequest> attempts;
}
