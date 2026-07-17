package com.remelearning.common.ai.tts.google;

import com.remelearning.common.ai.tts.TtsClient;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

/**
 * Builds the {@link TtsClient} bean from {@code reme.tts.*} properties when
 * {@code reme.tts.provider=google}. The default provider is now {@code supertonic} (see
 * {@code ai.tts.supertonic.SupertonicTtsClientConfig}); set {@code reme.tts.provider=google} to use
 * this one instead. To add another provider, add a sibling config class with its own
 * {@code @ConditionalOnProperty(havingValue = "...")} - callers only ever depend on {@link TtsClient}.
 */
@Configuration
@ConfigurationProperties(prefix = "reme.tts.google")
public class GoogleCloudTtsClientConfig {

	private static final String GOOGLE_TTS_API_BASE_URL = "https://texttospeech.googleapis.com";

	private String apiKey;

	// Only registered when reme.tts.provider=google (or unset), so exactly one TtsClient bean
	// exists at a time; reuses Spring Boot's auto-configured RestClient.Builder rather than a
	// bare RestClient.builder() so shared HTTP config (timeouts, interceptors) still applies.
	@Bean
	@ConditionalOnProperty(prefix = "reme.tts", name = "provider", havingValue = "google", matchIfMissing = true)
	public TtsClient googleCloudTtsClient(RestClient.Builder restClientBuilder) {
		RestClient restClient = restClientBuilder.baseUrl(GOOGLE_TTS_API_BASE_URL).build();
		return new GoogleCloudTtsClient(restClient, apiKey);
	}

	public void setApiKey(String apiKey) {
		this.apiKey = apiKey;
	}
}
