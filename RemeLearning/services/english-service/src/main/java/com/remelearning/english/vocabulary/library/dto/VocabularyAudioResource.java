package com.remelearning.english.vocabulary.library.dto;

import java.io.InputStream;

/** A library word's synthesized-audio stream, ready to write straight into an HTTP response body. */
public record VocabularyAudioResource(InputStream stream, long size, String mimeType, String filename) {
}
