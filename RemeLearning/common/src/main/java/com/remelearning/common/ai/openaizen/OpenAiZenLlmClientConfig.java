package com.remelearning.common.ai.openaizen;

import com.remelearning.common.ai.LlmClient;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

/**
 * Builds the {@link LlmClient} bean from {@code reme.llm.*} properties when
 * {@code reme.llm.provider=zen} — opencode.ai Zen, an OpenAI-compatible endpoint
 * ({@code https://opencode.ai/zen/v1/chat/completions}) defaulting to model {@code big-pickle}.
 * To add another provider later, add a sibling config class with its own
 * {@code @ConditionalOnProperty(havingValue = "...")} — callers only ever depend on {@link LlmClient}.
 */
@Configuration
@ConfigurationProperties(prefix = "reme.llm.zen")
public class OpenAiZenLlmClientConfig {

	private static final String ZEN_API_BASE_URL = "https://opencode.ai/zen";

	private String apiKey;
	private String model = "big-pickle";

	// Only registered when reme.llm.provider=zen, so exactly one LlmClient bean exists at a time;
	// reuses Spring Boot's auto-configured RestClient.Builder so shared HTTP config
	// (timeouts, interceptors) still applies.
	@Bean
	@ConditionalOnProperty(prefix = "reme.llm", name = "provider", havingValue = "zen")
	public LlmClient openAiZenLlmClient(RestClient.Builder restClientBuilder) {
		RestClient restClient = restClientBuilder.baseUrl(ZEN_API_BASE_URL).build();
		return new OpenAiZenLlmClient(restClient, apiKey, model);
	}

	public void setApiKey(String apiKey) {
		this.apiKey = apiKey;
	}

	public void setModel(String model) {
		this.model = model;
	}
}