package com.remelearning.bff.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.web.client.RestClient;

/**
 * Local Maven cache is missing the Spring Boot 4 split-module RestClient.Builder/Jackson
 * ObjectMapper autoconfiguration (see the other services' Boot4CompatConfig). bff-service needs
 * both beans since common's GeminiLlmClientConfig and AiServiceSentenceAlignmentClientConfig get
 * component-scanned here too, even though bff-service never calls either directly.
 */
@Configuration
public class Boot4CompatConfig {

	@Bean
	@ConditionalOnMissingBean(RestClient.Builder.class)
	public RestClient.Builder restClientBuilder(ObjectMapper objectMapper) {
		// Register Jackson converter so .body(someRecord) serializes to JSON correctly —
		// without this the RestClient sends an empty body (Boot 4.1.0 no longer auto-registers
		// a RestClient.Builder bean with pre-configured converters).
		// Also force SimpleClientHttpRequestFactory: RestClient's default factory pick in this
		// environment (JDK HttpClient) silently sends an EMPTY body over real HTTP for POST
		// requests, even though the same request looks fine in mocked/unit tests (MockRestServiceServer
		// bypasses the real transport) — server sees "Field required" at ["body"]. SimpleClientHttpRequestFactory
		// fully buffers the request body before sending, avoiding that streaming bug.
		return RestClient.builder()
				.requestFactory(new org.springframework.http.client.SimpleClientHttpRequestFactory())
				.messageConverters(converters -> converters.add(
						new org.springframework.http.converter.json.MappingJackson2HttpMessageConverter(objectMapper)));
	}

	@Bean
	@ConditionalOnMissingBean(ObjectMapper.class)
	public ObjectMapper objectMapper() {
		return new ObjectMapper();
	}
}
