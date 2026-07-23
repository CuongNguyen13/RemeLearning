package com.remelearning.english.speaking.dto;

import java.io.InputStream;

/** A loaded audio stream ready to be written into an HTTP response, mirroring dictation's equivalent. */
public record SpeakingAudioResource(InputStream stream, long contentLength, String contentType, String filename) {
}
