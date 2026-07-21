package com.remelearning.common.ai.openaizen;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * JSON response from an OpenAI-compatible {@code POST /v1/chat/completions} call.
 * Only the fields needed to build {@link com.remelearning.common.ai.LlmResponse} are declared.
 */
public record OpenAiZenChatResponse(
		List<Choice> choices,
		Usage usage
) {

	public record Choice(Message message) {}

	public record Message(String role, String content) {}

	public record Usage(
			@JsonProperty("prompt_tokens")
			int promptTokens,
			@JsonProperty("completion_tokens")
			int completionTokens
	) {}
}