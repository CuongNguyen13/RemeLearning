package com.remelearning.english.listening.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

/** Submitted answers, aligned by index to the practice item's questions. */
@Data
public class SubmitListeningAttemptRequest {
	@NotBlank
	private String userId;

	@NotNull
	private Long practiceItemId;

	@NotEmpty
	private List<String> answers;
}
