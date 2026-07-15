package com.remelearning.bff.client;

import com.remelearning.bff.dto.RecommendationDto;
import com.remelearning.common.response.ApiResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;

/** Thin wrapper around recommendation-service's REST API. */
@Slf4j
@Component
public class RecommendationServiceClient {

	private final WebClient recommendationServiceClient;

	public RecommendationServiceClient(@Qualifier("recommendationServiceClient") WebClient recommendationServiceClient) {
		this.recommendationServiceClient = recommendationServiceClient;
	}

	/** Fetches a learner's recommendations grouped by category (vocabulary/grammar/pronunciation). */
	public Mono<Map<String, List<RecommendationDto>>> getGroupedRecommendations(String userId) {
		return recommendationServiceClient.get()
				.uri("/api/v1/recommendations/{userId}/grouped", userId)
				.retrieve()
				.bodyToMono(new ParameterizedTypeReference<ApiResponse<Map<String, List<RecommendationDto>>>>() {})
				.map(ApiResponse::getData)
				.doOnError(ex -> log.error("Failed to fetch grouped recommendations for userId={}", userId, ex));
	}
}
