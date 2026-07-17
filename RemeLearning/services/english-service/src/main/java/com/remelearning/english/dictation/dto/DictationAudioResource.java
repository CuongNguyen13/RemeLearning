package com.remelearning.english.dictation.dto;

import java.io.InputStream;

/**
 * A playable audio stream loaded from storage, for the clip/practice audio streaming endpoints. The
 * controller wraps this into an HTTP response; the caller is responsible for closing {@code stream}.
 */
public record DictationAudioResource(InputStream stream, long contentLength, String contentType, String filename) {
}
