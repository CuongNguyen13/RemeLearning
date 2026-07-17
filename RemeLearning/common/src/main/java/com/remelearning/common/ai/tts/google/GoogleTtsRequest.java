package com.remelearning.common.ai.tts.google;

/** Wire shape for Google Cloud TTS's {@code text:synthesize} request body. */
record GoogleTtsRequest(Input input, Voice voice, AudioConfig audioConfig) {

	record Input(String text) {
	}

	record Voice(String languageCode, String name) {
	}

	record AudioConfig(String audioEncoding) {
	}
}
