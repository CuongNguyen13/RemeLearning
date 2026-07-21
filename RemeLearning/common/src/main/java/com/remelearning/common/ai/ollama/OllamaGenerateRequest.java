package com.remelearning.common.ai.ollama;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * JSON body for Ollama's {@code POST /api/generate} endpoint (non-streaming).
 * Uses Ollama's native {@code system} field (available since 0.3.0) rather than
 * embedding the system prompt in the prompt string; temperature goes inside
 * the nested {@code options} object per the Ollama wire contract.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record OllamaGenerateRequest(
		String model,
		String system,
		String prompt,
		boolean stream,
		OllamaGenerateOptions options
) {

	/** Nested {@code options} object for temperature and other generation params. */
	@JsonInclude(JsonInclude.Include.NON_NULL)
	public record OllamaGenerateOptions(
			Double temperature
	) {}
}