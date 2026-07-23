package com.remelearning.common.ai.pronunciation.aiservice;

import com.remelearning.common.ai.pronunciation.PronunciationScoringClient;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

/**
 * Builds the {@link PronunciationScoringClient} bean pointing at ai-service from
 * {@code reme.pronunciation.ai-service.*}. Read timeout defaults high since scoring loads a
 * wav2vec2 model and runs a full forward pass on CPU - see ai-service's
 * {@code app/pronunciation/gop_model.py}.
 */
@Configuration
@ConfigurationProperties(prefix = "reme.pronunciation.ai-service")
public class AiServicePronunciationScoringClientConfig {

	/** Base URL of the ai-service exposing {@code POST /api/v1/pronunciation/score}. */
	private String baseUrl = "http://localhost:8000";

	/** How long to wait for ai-service to finish scoring one attempt. */
	private int readTimeoutSeconds = 60;

	@Bean
	@ConditionalOnMissingBean(PronunciationScoringClient.class)
	public PronunciationScoringClient pronunciationScoringClient(RestClient.Builder restClientBuilder) {
		SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
		requestFactory.setConnectTimeout(5_000);
		requestFactory.setReadTimeout(readTimeoutSeconds * 1000);

		RestClient restClient = restClientBuilder.baseUrl(baseUrl).requestFactory(requestFactory).build();
		return new AiServicePronunciationScoringClient(restClient);
	}

	public void setBaseUrl(String baseUrl) {
		this.baseUrl = baseUrl;
	}

	public void setReadTimeoutSeconds(int readTimeoutSeconds) {
		this.readTimeoutSeconds = readTimeoutSeconds;
	}
}
