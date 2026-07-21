package com.remelearning.common.ai.ollama;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * JSON response from Ollama's {@code POST /api/generate} endpoint (non-streaming).
 * Only the fields needed for building {@link com.remelearning.common.ai.LlmResponse} are declared;
 * Ollama returns many more metrics (total_duration, load_duration, etc.) that we ignore.
 */
public record OllamaGenerateResponse(
		String response,
		@JsonProperty("prompt_eval_count")
		int promptEvalCount,
		@JsonProperty("eval_count")
		int evalCount
) {}