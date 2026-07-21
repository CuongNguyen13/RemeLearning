package com.remelearning.common.ai.ollama;

import com.remelearning.common.ai.LlmClient;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

/**
 * Builds the {@link LlmClient} bean from {@code reme.llm.*} properties when
 * {@code reme.llm.provider=ollama}. Uses {@code http://localhost:11434} as the default
 * base URL (Ollama's default listen address); model must be set explicitly via
 * {@code reme.llm.ollama.model} since there is no sensible default across projects.
 *
 * <p>Only one provider bean is active at a time, gated by distinct
 * {@code @ConditionalOnProperty(havingValue)} values on each config class
 * (Gemini: gemini, Ollama: ollama) — callers only ever depend on {@link LlmClient}.
 */
@Configuration
@ConfigurationProperties(prefix = "reme.llm.ollama")
public class OllamaLlmClientConfig {

	private static final String OLLAMA_BASE_URL = "http://localhost:11434";

	private String model = "deepseek-v4-flash:cloud";

	// Only registered when reme.llm.provider=ollama, so exactly one LlmClient bean exists.
	// Uses Spring Boot's auto-configured RestClient.Builder so shared HTTP config
	// (timeouts, interceptors) applies; baseUrl is set to the Ollama server address.
	@Bean
	@ConditionalOnProperty(prefix = "reme.llm", name = "provider", havingValue = "ollama")
	public LlmClient ollamaLlmClient(RestClient.Builder restClientBuilder) {
		RestClient restClient = restClientBuilder.baseUrl(OLLAMA_BASE_URL).build();
		return new OllamaLlmClient(restClient, model);
	}

	public void setModel(String model) {
		this.model = model;
	}
}