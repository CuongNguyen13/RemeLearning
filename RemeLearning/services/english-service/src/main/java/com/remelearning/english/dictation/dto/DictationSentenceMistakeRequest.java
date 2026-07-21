package com.remelearning.english.dictation.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.Data;

/**
 * One incorrect attempt at a single sentence during sentence-mode practice, submitted alongside the
 * clip's final (correct) transcript. The learner must retype the sentence correctly to advance in the
 * FE, so these never appear in the final transcript's word diff - carrying them separately lets
 * {@link com.remelearning.english.dictation.service.DictationServiceImpl#submitAttempt} fold each one
 * into the same miss/weak-point pipeline as a regular wrong answer.
 */
@Data
public class DictationSentenceMistakeRequest {

	/** The sentence's 0-based position within the clip, matching {@code DictationSentenceDto.index}. */
	@NotNull
	@PositiveOrZero
	private Integer sentenceIndex;

	/** The sentence's correct reference text. */
	@NotBlank
	private String expectedText;

	/** What the learner actually typed on that failed check. */
	@NotBlank
	private String attemptedText;
}
