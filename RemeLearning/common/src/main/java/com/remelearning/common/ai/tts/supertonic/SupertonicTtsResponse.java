package com.remelearning.common.ai.tts.supertonic;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Wire response from ai-service's {@code POST /api/v1/tts/synthesize} (snake_case keys, mapped here
 * with {@link JsonProperty} so the service's default camelCase mapper still decodes it correctly).
 * Package-private - never exposed outside this implementation package.
 */
record SupertonicTtsResponse(
		@JsonProperty("audio_base64") String audioBase64,
		@JsonProperty("mime_type") String mimeType,
		@JsonProperty("sample_rate") int sampleRate) {
}
