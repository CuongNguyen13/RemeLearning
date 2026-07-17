package com.remelearning.bff.client;

import com.remelearning.bff.dto.DashboardSummaryDto;
import com.remelearning.common.response.ApiResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

/** Thin wrapper around dashboard-service's REST API. */
@Slf4j
@Component
public class DashboardServiceClient {

	private final WebClient dashboardServiceClient;

	public DashboardServiceClient(@Qualifier("dashboardServiceWebClient") WebClient dashboardServiceClient) {
		this.dashboardServiceClient = dashboardServiceClient;
	}

	/** Fetches a learner's dashboard summary (category progress + recent recommendations). */
	public Mono<DashboardSummaryDto> getSummary(String userId) {
		return dashboardServiceClient.get()
				.uri("/api/v1/dashboard/{userId}", userId)
				.retrieve()
				.bodyToMono(new ParameterizedTypeReference<ApiResponse<DashboardSummaryDto>>() {})
				.map(ApiResponse::getData)
				.doOnError(ex -> log.error("Failed to fetch dashboard summary for userId={}", userId, ex));
	}
}
