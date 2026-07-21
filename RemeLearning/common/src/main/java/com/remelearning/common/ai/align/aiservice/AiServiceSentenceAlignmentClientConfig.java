package com.remelearning.common.ai.align.aiservice;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.remelearning.common.ai.align.SentenceAlignmentClient;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

/**
 * Builds the {@link SentenceAlignmentClient} bean pointing at ai-service from
 * {@code reme.alignment.ai-service.*}. {@code @ConditionalOnMissingBean} (rather than a provider
 * switch like {@code reme.tts.provider}) since ai-service's Whisper-based alignment is the only
 * implementation today - a future alternative can still override this bean without touching
 * callers. Read timeout defaults high because aligning a clip synchronously transcribes its whole
 * audio with word timestamps, which can take well beyond typical HTTP timeouts on CPU.
 */
@Configuration
@ConfigurationProperties(prefix = "reme.alignment.ai-service")
public class AiServiceSentenceAlignmentClientConfig {

	/** Base URL of the ai-service exposing {@code POST /api/v1/dictation/align-sentences}. */
	private String baseUrl = "http://localhost:8000";

	/** How long to wait for ai-service to finish transcribing + aligning one clip. */
	private int readTimeoutSeconds = 120;

	@Bean
	@ConditionalOnMissingBean(SentenceAlignmentClient.class)
	public SentenceAlignmentClient sentenceAlignmentClient(RestClient.Builder restClientBuilder, ObjectMapper objectMapper) {
		SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
		requestFactory.setConnectTimeout(5_000);
		requestFactory.setReadTimeout(readTimeoutSeconds * 1000);

		RestClient restClient = restClientBuilder.baseUrl(baseUrl).requestFactory(requestFactory).build();
		return new AiServiceSentenceAlignmentClient(restClient, objectMapper);
	}

	public void setBaseUrl(String baseUrl) {
		this.baseUrl = baseUrl;
	}

	public void setReadTimeoutSeconds(int readTimeoutSeconds) {
		this.readTimeoutSeconds = readTimeoutSeconds;
	}
}
