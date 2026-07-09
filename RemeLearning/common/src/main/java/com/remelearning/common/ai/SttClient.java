package com.remelearning.common.ai;

import java.io.InputStream;

/**
 * Vendor-neutral contract for speech-to-text transcription.
 * Concrete implementations (Whisper, AWS Transcribe, ...) live outside {@code common}.
 */
public interface SttClient {

	/**
	 * Transcribes an audio stream into text with per-segment timestamps.
	 *
	 * @param audio        raw audio content (already normalized upstream, e.g. via FFmpeg)
	 * @param languageCode BCP-47 language hint, e.g. "en-US"
	 */
	TranscriptionResult transcribe(InputStream audio, String languageCode);
}
