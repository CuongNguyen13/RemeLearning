package com.remelearning.common.ai.gemini;

import com.remelearning.common.ai.LlmClient;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

/**
 * Builds the {@link LlmClient} bean from {@code reme.llm.*} properties when
 * {@code reme.llm.provider=gemini} (the default). To add another provider later,
 * add a sibling config class (e.g. {@code ai.openai.OpenAiLlmClientConfig}) with its own
 * {@code @ConditionalOnProperty(havingValue = "openai")} - callers only ever depend on {@link LlmClient}.
 */
@Configuration
@ConfigurationProperties(prefix = "reme.llm.gemini")
public class GeminiLlmClientConfig {

	private static final String GEMINI_API_BASE_URL = "https://generativelanguage.googleapis.com";

	private String apiKey;
	private String model = "gemini-2.0-flash";

	// Only registered when reme.llm.provider=gemini (or unset), so exactly one LlmClient bean
	// exists at a time; reuses Spring Boot's auto-configured RestClient.Builder rather than a
	// bare RestClient.builder() so shared HTTP config (timeouts, interceptors) still applies.
	@Bean
	@ConditionalOnProperty(prefix = "reme.llm", name = "provider", havingValue = "gemini", matchIfMissing = true)
	public LlmClient geminiLlmClient(RestClient.Builder restClientBuilder) {
		RestClient restClient = restClientBuilder.baseUrl(GEMINI_API_BASE_URL).build();
		return new GeminiLlmClient(restClient, apiKey, model);
	}

	public void setApiKey(String apiKey) {
		this.apiKey = apiKey;
	}

	public void setModel(String model) {
		this.model = model;
	}
}
