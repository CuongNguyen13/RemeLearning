package com.remelearning.common.ai.tts;

import lombok.Builder;
import lombok.Getter;

/** Input payload for a {@link TtsClient} synthesis call. */
@Getter
@Builder
public class TtsRequest {

	/** The text to synthesize into speech. */
	private String text;
	/** BCP-47 language/accent code, e.g. {@code en-US}, {@code en-GB}, {@code en-AU}. */
	private String languageCode;
	/** Provider-specific voice identifier, e.g. Google {@code en-US-Neural2-C}. */
	private String voiceName;
	/** Provider-neutral voice/style preset, e.g. a Supertonic preset {@code F1}/{@code M2}; null uses the provider default. */
	private String voice;
	/** Output audio container; defaults to MP3 for easy browser playback. */
	@Builder.Default
	private String audioEncoding = "MP3";
}
