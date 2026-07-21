package com.remelearning.common.ai.openaizen;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * JSON body for an OpenAI-compatible {@code POST /v1/chat/completions} call, as used by
 * opencode.ai Zen. Field names map to the OpenAI wire contract via {@code @JsonProperty}
 * where it differs from Java camelCase (e.g. {@code max_tokens}).
 *
 * <p>{@code reasoning} is OpenRouter's extension (Zen proxies OpenRouter-style models):
 * some routed models (e.g. {@code big-pickle} -> a reasoning model) spend the entire
 * {@code max_tokens} budget on their internal chain-of-thought and finish with
 * {@code finish_reason=length} before ever writing the final answer, leaving {@code content}
 * null. Always disabling reasoning keeps responses deterministic and budget-bounded for this
 * app's short classification/analysis prompts, which don't need visible chain-of-thought.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record OpenAiZenChatRequest(
		String model,
		List<Message> messages,
		double temperature,
		@JsonProperty("max_tokens")
		Integer maxTokens,
		Reasoning reasoning
) {

	/** One chat message; {@code role} is one of {@code system}, {@code user}, {@code assistant}. */
	public record Message(String role, String content) {}

	/** OpenRouter-style reasoning toggle; {@code enabled=false} suppresses the reasoning field entirely. */
	public record Reasoning(boolean enabled) {}
}