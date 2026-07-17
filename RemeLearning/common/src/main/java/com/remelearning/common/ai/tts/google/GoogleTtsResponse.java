package com.remelearning.common.ai.tts.google;

/** Wire shape for Google Cloud TTS's {@code text:synthesize} response body (base64-encoded audio). */
record GoogleTtsResponse(String audioContent) {
}
