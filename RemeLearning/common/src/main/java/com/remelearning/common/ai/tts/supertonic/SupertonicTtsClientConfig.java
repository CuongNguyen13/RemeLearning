package com.remelearning.common.ai.tts.supertonic;

import com.remelearning.common.ai.tts.TtsClient;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

/**
 * Builds the {@link TtsClient} bean pointing at ai-service from {@code reme.tts.supertonic.*} when
 * {@code reme.tts.provider=supertonic} (the default). Exactly one {@link TtsClient} bean exists at a
 * time: setting {@code reme.tts.provider=google} activates {@code ai.tts.google.GoogleCloudTtsClientConfig}
 * instead. Reuses Spring Boot's auto-configured {@link RestClient.Builder} so shared HTTP config applies.
 */
@Configuration
@ConfigurationProperties(prefix = "reme.tts.supertonic")
public class SupertonicTtsClientConfig {

	/** Base URL of the ai-service exposing {@code POST /api/v1/tts/synthesize}. */
	private String baseUrl = "http://localhost:8000";

	@Bean
	@ConditionalOnProperty(prefix = "reme.tts", name = "provider", havingValue = "supertonic", matchIfMissing = true)
	public TtsClient supertonicTtsClient(RestClient.Builder restClientBuilder) {
		return new SupertonicTtsClient(restClientBuilder.baseUrl(baseUrl).build());
	}

	public void setBaseUrl(String baseUrl) {
		this.baseUrl = baseUrl;
	}
}
