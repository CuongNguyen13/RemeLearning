package com.remelearning.common.ai.tts.supertonic;

/**
 * Wire request for ai-service's {@code POST /api/v1/tts/synthesize}. Package-private: this
 * vendor-specific shape never leaves this package (callers use the neutral {@code TtsRequest}).
 *
 * @param text  the text to synthesize
 * @param lang  short language code Supertonic expects, e.g. {@code en}
 * @param voice preset voice id (e.g. {@code F1}); null lets ai-service pick its default
 */
record SupertonicTtsRequest(String text, String lang, String voice) {
}
