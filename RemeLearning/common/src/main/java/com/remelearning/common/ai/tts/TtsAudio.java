package com.remelearning.common.ai.tts;

import lombok.Builder;
import lombok.Getter;

/** Result of a {@link TtsClient} synthesis call. */
@Getter
@Builder
public class TtsAudio {

	/** Raw synthesized audio bytes, in the container requested via {@link TtsRequest#getAudioEncoding()}. */
	private byte[] audioBytes;
	/** MIME type of {@link #audioBytes}, e.g. {@code audio/mpeg}. */
	private String mimeType;
	/** Sample rate of {@link #audioBytes} in Hz (e.g. 44100 for Supertonic); 0 if unknown. */
	private int sampleRate;
}
