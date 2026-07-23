package com.remelearning.english.vocabulary.learn.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

/** Submitted answers, aligned by index to the practice item's questions. */
@Data
public class SubmitVocabAttemptRequest {
	@NotBlank
	private String userId;

	@NotNull
	private Long practiceItemId;

	@NotEmpty
	private List<String> answers;
}
