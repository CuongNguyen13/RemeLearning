package com.remelearning.bff.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.web.client.RestClient;

/**
 * Local Maven cache is missing the Spring Boot 4 split-module RestClient.Builder
 * autoconfiguration (see the other services' Boot4CompatConfig). bff-service only needs this one
 * bean, since common's GeminiLlmClientConfig gets component-scanned here too even though
 * bff-service never calls an LLM directly.
 */
@Configuration
public class Boot4CompatConfig {

	@Bean
	@ConditionalOnMissingBean(RestClient.Builder.class)
	public RestClient.Builder restClientBuilder() {
		return RestClient.builder();
	}
}
