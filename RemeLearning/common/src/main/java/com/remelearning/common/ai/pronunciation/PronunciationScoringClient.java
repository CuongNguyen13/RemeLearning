package com.remelearning.common.ai.pronunciation;

import java.io.InputStream;

/**
 * Vendor-neutral contract for scoring a learner's spoken attempt at a target sentence
 * (Goodness-of-Pronunciation). The concrete implementation (ai-service's wav2vec2-based GOP
 * model) lives outside {@code common} so the scoring provider can be swapped without touching
 * callers - same pattern as {@link com.remelearning.common.ai.align.SentenceAlignmentClient}.
 */
public interface PronunciationScoringClient {

	/**
	 * @param audio        the learner's recorded audio
	 * @param audioFilename original filename (extension tells ai-service how to decode the container)
	 * @param expectedText  the sentence the learner was asked to say
	 * @param languageCode  BCP-47-ish code (e.g. "en") for the reference transcript's ASR pass
	 */
	PronunciationScore score(InputStream audio, String audioFilename, String expectedText, String languageCode);
}
