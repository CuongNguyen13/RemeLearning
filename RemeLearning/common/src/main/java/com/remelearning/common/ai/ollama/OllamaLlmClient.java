package com.remelearning.common.ai.ollama;

import com.remelearning.common.ai.LlmClient;
import com.remelearning.common.ai.LlmRequest;
import com.remelearning.common.ai.LlmResponse;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Ollama-backed {@link LlmClient} implementation that calls the Ollama HTTP API
 * ({@code POST /api/generate}) with {@code stream: false}. Unlike cloud LLM providers,
 * Ollama runs locally so no API key or SSL is needed — just a {@code base-url} pointing at
 * the Ollama server (default {@code http://localhost:11434}).
 *
 * <p>Registered by {@link OllamaLlmClientConfig} when {@code reme.llm.provider=ollama};
 * other providers (Gemini, etc.) coexist as sibling packages without touching callers.
 */
public class OllamaLlmClient implements LlmClient {

	private static final String GENERATE_PATH = "/api/generate";

	private final RestClient restClient;
	private final String model;

	public OllamaLlmClient(RestClient restClient, String model) {
		this.restClient = restClient;
		this.model = model;
	}

	// Builds the Ollama wire request from the vendor-neutral LlmRequest, POSTs it to
	// /api/generate, then maps the Ollama response back to the vendor-neutral LlmResponse.
	@Override
	public LlmResponse complete(LlmRequest request) {
		OllamaGenerateRequest body = toOllamaRequest(request);

		OllamaGenerateResponse response = restClient.post()
				.uri(GENERATE_PATH)
				.contentType(MediaType.APPLICATION_JSON)
				.body(body)
				.retrieve()
				.body(OllamaGenerateResponse.class);

		return toLlmResponse(response);
	}

	// Maps the vendor-neutral LlmRequest onto Ollama's /api/generate JSON shape.
	// Uses Ollama's native system field for the system prompt, and concatenates
	// history turns + user prompt into the main prompt string since Ollama's
	// /api/generate is single-turn (no conversation array).
	private OllamaGenerateRequest toOllamaRequest(LlmRequest request) {
		String system = nullIfBlank(request.getSystemPrompt());
		String prompt = buildPrompt(request);
		OllamaGenerateRequest.OllamaGenerateOptions options = new OllamaGenerateRequest.OllamaGenerateOptions(
				request.getTemperature());
		return new OllamaGenerateRequest(model, system, prompt, false, options);
	}

	// Extracts the response text and token-usage fields into the vendor-neutral LlmResponse;
	// Ollama /api/generate returns a flat JSON with "response", "prompt_eval_count", "eval_count".
	private LlmResponse toLlmResponse(OllamaGenerateResponse response) {
		if (response == null || response.response() == null) {
			throw new IllegalStateException("Ollama returned an empty response");
		}
		return LlmResponse.builder()
				.content(response.response())
				.model(model)
				.promptTokens(response.promptEvalCount())
				.completionTokens(response.evalCount())
				.build();
	}

	// Builds a single prompt string from history turns (if any) and the current user prompt.
	// History turns are labeled with their role for clarity.
	private String buildPrompt(LlmRequest request) {
		List<Map<String, String>> history = request.getHistory();
		String userPrompt = request.getUserPrompt();

		if (history == null || history.isEmpty()) {
			return userPrompt;
		}

		StringBuilder sb = new StringBuilder();
		for (Map<String, String> turn : history) {
			String role = turn.getOrDefault("role", "user");
			String content = turn.getOrDefault("content", "");
			sb.append("[").append(role).append("]\n");
			sb.append(content).append("\n\n");
		}
		sb.append("[User]\n");
		sb.append(userPrompt);
		return sb.toString();
	}

	// Returns null for a null or all-blank string, otherwise the trimmed value.
	private static String nullIfBlank(String s) {
		if (s == null || s.isBlank()) {
			return null;
		}
		return s.trim();
	}
}