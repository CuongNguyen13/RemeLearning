package com.remelearning.common.storage;

/**
 * Resolves the HTTP content-type and filename extension for an audio object from its storage key,
 * shared by every service that streams back audio it either stored verbatim (legacy {@code .mp3}
 * content-library clips) or generated/transcoded itself ({@code .opus}, the default for anything
 * newly written since Opus transcoding was introduced; {@code .wav} remains the fallback for any
 * pre-existing uncompressed object).
 */
public final class AudioContentTypes {

	private AudioContentTypes() {
	}

	/** The extension (including the leading dot) newly-written generated/transcoded audio should use. */
	public static final String OPUS_EXTENSION = ".opus";

	public static String contentType(String storageKey) {
		String lower = storageKey.toLowerCase();
		if (lower.endsWith(".mp3")) {
			return "audio/mpeg";
		}
		if (lower.endsWith(".opus")) {
			return "audio/ogg";
		}
		return "audio/wav";
	}

	public static String extension(String storageKey) {
		String lower = storageKey.toLowerCase();
		if (lower.endsWith(".mp3")) {
			return ".mp3";
		}
		if (lower.endsWith(".opus")) {
			return ".opus";
		}
		return ".wav";
	}
}
