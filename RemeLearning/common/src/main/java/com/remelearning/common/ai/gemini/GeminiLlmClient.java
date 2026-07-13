package com.remelearning.common.ai.gemini;

import com.remelearning.common.ai.LlmClient;
import com.remelearning.common.ai.LlmRequest;
import com.remelearning.common.ai.LlmResponse;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Gemini-backed {@link LlmClient} implementation.
 * Registered by {@link GeminiLlmClientConfig} when {@code reme.llm.provider=gemini} (the default);
 * other providers can be added later as sibling packages (e.g. {@code ai.openai}) without touching callers.
 */
public class GeminiLlmClient implements LlmClient {

	private static final String GENERATE_CONTENT_PATH = "/v1beta/models/{model}:generateContent?key={apiKey}";

	private final RestClient restClient;
	private final String apiKey;
	private final String model;

	public GeminiLlmClient(RestClient restClient, String apiKey, String model) {
		this.restClient = restClient;
		this.apiKey = apiKey;
		this.model = model;
	}

	// Builds the Gemini wire request from the vendor-neutral LlmRequest, POSTs it to
	// generateContent, then maps the Gemini wire response back to the vendor-neutral LlmResponse.
	@Override
	public LlmResponse complete(LlmRequest request) {
		GeminiRequest body = toGeminiRequest(request);

		GeminiResponse response = restClient.post()
				.uri(GENERATE_CONTENT_PATH, model, apiKey)
				.contentType(MediaType.APPLICATION_JSON)
				.body(body)
				.retrieve()
				.body(GeminiResponse.class);

		return toLlmResponse(response);
	}

	// Maps systemPrompt/history/userPrompt onto Gemini's systemInstruction + contents[] shape,
	// appending the current userPrompt as the final "user" turn after any prior history.
	private GeminiRequest toGeminiRequest(LlmRequest request) {
		GeminiRequest.SystemInstruction systemInstruction = request.getSystemPrompt() == null
				? null
				: new GeminiRequest.SystemInstruction(List.of(new GeminiRequest.Part(request.getSystemPrompt())));

		List<GeminiRequest.Content> contents = request.getHistory().stream()
				.map(this::toGeminiContent)
				.collect(Collectors.toCollection(ArrayList::new));
		contents.add(new GeminiRequest.Content("user", List.of(new GeminiRequest.Part(request.getUserPrompt()))));

		GeminiRequest.GenerationConfig generationConfig = new GeminiRequest.GenerationConfig(
				request.getTemperature(), request.getMaxOutputTokens());

		return new GeminiRequest(systemInstruction, contents, generationConfig);
	}

	// Converts one LlmRequest.history turn ({"role": ..., "content": ...}) into a Gemini Content;
	// Gemini calls the assistant role "model" rather than "assistant".
	private GeminiRequest.Content toGeminiContent(Map<String, String> turn) {
		String role = "assistant".equalsIgnoreCase(turn.get("role")) ? "model" : "user";
		return new GeminiRequest.Content(role, List.of(new GeminiRequest.Part(turn.get("content"))));
	}

	// Extracts the first candidate's text and token-usage counters into the vendor-neutral LlmResponse;
	// an empty/missing candidates list means the prompt was blocked (e.g. by a safety filter).
	private LlmResponse toLlmResponse(GeminiResponse response) {
		if (response == null || response.candidates() == null || response.candidates().isEmpty()) {
			throw new IllegalStateException("Gemini returned no candidates (prompt may have been blocked)");
		}

		String text = response.candidates().get(0).content().parts().stream()
				.map(GeminiResponse.Part::text)
				.collect(Collectors.joining());

		GeminiResponse.UsageMetadata usage = response.usageMetadata();
		return LlmResponse.builder()
				.content(text)
				.model(model)
				.promptTokens(usage == null ? 0 : usage.promptTokenCount())
				.completionTokens(usage == null ? 0 : usage.candidatesTokenCount())
				.build();
	}
}
