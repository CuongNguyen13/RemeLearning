package com.remelearning.english.learn.common;

/**
 * Wraps any failure from {@link AiContentClient} (LLM call, empty/unparsable response) behind one
 * unchecked type, so every "learn" generator/scorer can degrade to its own fallback with a single
 * catch clause instead of juggling {@code JsonProcessingException}/{@code RestClientException}.
 */
public class AiContentException extends RuntimeException {

	public AiContentException(String message, Throwable cause) {
		super(message, cause);
	}

	public AiContentException(String message) {
		super(message);
	}
}
