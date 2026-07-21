package com.remelearning.common.ai.align;

import java.io.InputStream;
import java.util.List;

/**
 * Vendor-neutral contract for locating a known script's sentences within its own audio timeline
 * (speech-to-text-based forced alignment). Backed today by ai-service's Whisper word-timestamp
 * endpoint (see {@code ai.align.aiservice.AiServiceSentenceAlignmentClient}), registered at the
 * composition root - callers depend only on this interface.
 */
public interface SentenceAlignmentClient {

	/**
	 * Aligns {@code sentences} (in order) against {@code audio}. Returns one {@link SentenceTiming}
	 * per input sentence, same order/size as {@code sentences}; a sentence that couldn't be located
	 * comes back with null start/end rather than a guess. The caller owns {@code audio} and must
	 * close it.
	 */
	List<SentenceTiming> align(InputStream audio, String audioFilename, List<String> sentences);
}
