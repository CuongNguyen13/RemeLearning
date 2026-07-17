package com.remelearning.common.ai.tts;

/**
 * Vendor-neutral contract for synthesizing speech audio from text.
 * Concrete implementations (e.g. Google Cloud TTS, Amazon Polly) live outside {@code common}
 * so the TTS provider can be swapped per-service without touching callers.
 */
public interface TtsClient {

	/** Synthesizes the given text into an audio clip in the requested voice/accent. */
	TtsAudio synthesize(TtsRequest request);
}
