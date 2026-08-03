package com.remelearning.english.learn.common;

/**
 * Wraps any failure from {@link AiContentClient} (LLM call, empty/unparsable response) behind one
 * unchecked type. Generators/scorers do not catch it: AI content has no template fallback anywhere
 * in this service, so it propagates to the caller as a real error.
 */
public class AiContentException extends RuntimeException {

	public AiContentException(String message, Throwable cause) {
		super(message, cause);
	}

	public AiContentException(String message) {
		super(message);
	}
}
