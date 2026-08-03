package com.remelearning.common.ai.openaizen;

import com.remelearning.common.ai.LlmClient;
import com.remelearning.common.ai.LlmException;
import com.remelearning.common.ai.LlmRequest;
import com.remelearning.common.ai.LlmResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * OpenAI-compatible {@link LlmClient} implementation for opencode.ai Zen (model {@code big-pickle}
 * by default), which exposes a standard {@code POST /v1/chat/completions} endpoint authenticated
 * via a Bearer API key. Registered by {@link OpenAiZenLlmClientConfig} when
 * {@code reme.llm.provider=zen}; other providers (Gemini, Ollama) coexist as sibling packages
 * without touching callers.
 */
public class OpenAiZenLlmClient implements LlmClient {

	private static final String CHAT_COMPLETIONS_PATH = "/v1/chat/completions";

	private final RestClient restClient;
	private final String apiKey;
	private final String model;
	private final boolean reasoningEnabled;

	public OpenAiZenLlmClient(RestClient restClient, String apiKey, String model, boolean reasoningEnabled) {
		this.restClient = restClient;
		this.apiKey = apiKey;
		this.model = model;
		this.reasoningEnabled = reasoningEnabled;
	}

	// Builds the OpenAI-shaped chat request from the vendor-neutral LlmRequest, POSTs it to
	// /v1/chat/completions with a Bearer auth header, then maps the response back to LlmResponse.
	@Override
	public LlmResponse complete(LlmRequest request) {
		OpenAiZenChatRequest body = toChatRequest(request);

		OpenAiZenChatResponse response = restClient.post()
				.uri(CHAT_COMPLETIONS_PATH)
				.contentType(MediaType.APPLICATION_JSON)
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
				.body(body)
				.retrieve()
				.body(OpenAiZenChatResponse.class);

		return toLlmResponse(response);
	}

	// Maps systemPrompt/history/userPrompt onto OpenAI's messages[] array: an optional leading
	// "system" message, then history turns in order, then the current userPrompt as the final
	// "user" turn. reasoningEnabled defaults to false (see OpenAiZenLlmClientConfig) since a routed
	// reasoning model can otherwise burn the whole max_tokens budget on chain-of-thought (see
	// toLlmResponse below) - configurable via reme.llm.zen.reasoning-enabled / ZEN_REASONING_ENABLED
	// for callers that actually want visible reasoning.
	private OpenAiZenChatRequest toChatRequest(LlmRequest request) {
		List<OpenAiZenChatRequest.Message> messages = new ArrayList<>();

		if (request.getSystemPrompt() != null && !request.getSystemPrompt().isBlank()) {
			messages.add(new OpenAiZenChatRequest.Message("system", request.getSystemPrompt()));
		}

		for (Map<String, String> turn : request.getHistory()) {
			String role = turn.getOrDefault("role", "user");
			messages.add(new OpenAiZenChatRequest.Message(role, turn.getOrDefault("content", "")));
		}

		messages.add(new OpenAiZenChatRequest.Message("user", request.getUserPrompt()));

		return new OpenAiZenChatRequest(model, messages, request.getTemperature(), request.getMaxOutputTokens(),
				new OpenAiZenChatRequest.Reasoning(reasoningEnabled));
	}

	// Extracts the first choice's message content and token-usage counters into the
	// vendor-neutral LlmResponse; an empty/missing choices list means the call failed upstream.
	// A null content also means failure - reasoning-routed models behind Zen (e.g. big-pickle) can
	// burn the entire max_tokens budget on their internal "reasoning" field and finish with
	// finish_reason=length before ever writing the final answer, leaving content null with no error.
	// Throwing here (like Gemini's "no candidates" and Ollama's "empty response" checks) lets every
	// existing caller's catch(IllegalStateException | LlmException ...) fall back to templates
	// instead of NPEing.
	private LlmResponse toLlmResponse(OpenAiZenChatResponse response) {
		if (response == null || response.choices() == null || response.choices().isEmpty()) {
			throw new LlmException("OpenAI Zen returned no choices");
		}

		String content = response.choices().get(0).message().content();
		if (content == null || content.isBlank()) {
			throw new LlmException(
					"OpenAI Zen returned no content (model may have exhausted max_tokens on reasoning)");
		}
		OpenAiZenChatResponse.Usage usage = response.usage();

		return LlmResponse.builder()
				.content(content)
				.model(model)
				.promptTokens(usage == null ? 0 : usage.promptTokens())
				.completionTokens(usage == null ? 0 : usage.completionTokens())
				.build();
	}
}