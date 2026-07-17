package com.remelearning.bff.config;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;

/** Declares one {@link WebClient} bean per downstream domain service, base-URL preconfigured. */
@Configuration
@RequiredArgsConstructor
public class WebClientConfig {

	private final DownstreamServicesProperties services;

	@Bean
	public WebClient userServiceWebClient() {
		return WebClient.create(services.getUser());
	}

	@Bean
	public WebClient recordingServiceWebClient() {
		return WebClient.create(services.getRecording());
	}

	// vocabulary/grammar/pronunciation were merged into one english-service (port 8085); one client bean, not three
	@Bean
	public WebClient englishServiceWebClient() {
		return WebClient.create(services.getEnglish());
	}

	@Bean
	public WebClient recommendationServiceWebClient() {
		return WebClient.create(services.getRecommendation());
	}

	@Bean
	public WebClient dashboardServiceWebClient() {
		return WebClient.create(services.getDashboard());
	}
}
