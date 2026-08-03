package com.remelearning.common.ai;

/**
 * Unchecked failure from an {@link LlmClient} implementation that isn't a plain HTTP/transport error
 * (e.g. Gemini returning no candidates because the prompt was blocked). Kept alongside the transport
 * exception ({@code RestClientException}) as one of the {@link LlmClient} contract's two failure
 * families - callers translating LLM failures into their own exception type (e.g. english-service's
 * {@code AiContentClient}) must catch both. No caller substitutes template content for a failure:
 * the error reaches the API caller.
 */
public class LlmException extends RuntimeException {

	public LlmException(String message) {
		super(message);
	}

	public LlmException(String message, Throwable cause) {
		super(message, cause);
	}
}
