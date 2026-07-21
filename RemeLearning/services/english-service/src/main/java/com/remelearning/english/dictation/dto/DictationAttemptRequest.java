package com.remelearning.english.dictation.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.util.List;

/**
 * Payload for POST /api/v1/dictation/attempts - a learner's typed answer for one clip. Exactly one
 * of {@code clipId} (library clip) or {@code practiceItemId} (AI-practice clip) must be set; the
 * service rejects the request if neither is present.
 */
@Data
public class DictationAttemptRequest {

	@NotBlank
	private String userId;

	/** A fixed-library clip id; null when this is an AI-practice attempt. */
	private Long clipId;

	/** An AI-practice item id; null when this is a library-clip attempt. */
	private Long practiceItemId;

	@NotBlank
	private String userTranscript;

	/**
	 * Wrong attempts the learner made on individual sentences before eventually retyping each one
	 * correctly (sentence-mode dictation forces a correct retry before advancing) - null/empty for a
	 * plain or AI-practice attempt. Folded into the same miss/weak-point pipeline as the main transcript.
	 */
	@Valid
	private List<DictationSentenceMistakeRequest> sentenceMistakes;
}
