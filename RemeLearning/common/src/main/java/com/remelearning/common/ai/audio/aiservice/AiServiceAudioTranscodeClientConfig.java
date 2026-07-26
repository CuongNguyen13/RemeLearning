package com.remelearning.common.ai.audio.aiservice;

import com.remelearning.common.ai.audio.AudioTranscodeClient;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

/**
 * Builds the {@link AudioTranscodeClient} bean pointing at ai-service from
 * {@code reme.audio-transcode.ai-service.*}.
 */
@Configuration
@ConfigurationProperties(prefix = "reme.audio-transcode.ai-service")
public class AiServiceAudioTranscodeClientConfig {

	/** Base URL of the ai-service exposing {@code POST /api/v1/audio/transcode/opus}. */
	private String baseUrl = "http://localhost:8000";

	/** How long to wait for ai-service to finish transcoding one clip. */
	private int readTimeoutSeconds = 30;

	@Bean
	@ConditionalOnMissingBean(AudioTranscodeClient.class)
	public AudioTranscodeClient audioTranscodeClient(RestClient.Builder restClientBuilder) {
		SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
		requestFactory.setConnectTimeout(5_000);
		requestFactory.setReadTimeout(readTimeoutSeconds * 1000);

		RestClient restClient = restClientBuilder.baseUrl(baseUrl).requestFactory(requestFactory).build();
		return new AiServiceAudioTranscodeClient(restClient);
	}

	public void setBaseUrl(String baseUrl) {
		this.baseUrl = baseUrl;
	}

	public void setReadTimeoutSeconds(int readTimeoutSeconds) {
		this.readTimeoutSeconds = readTimeoutSeconds;
	}
}
