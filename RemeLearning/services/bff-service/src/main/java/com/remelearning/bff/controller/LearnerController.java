package com.remelearning.bff.controller;

import com.remelearning.bff.client.RecommendationServiceClient;
import com.remelearning.bff.dto.LearnerOverviewResponse;
import com.remelearning.bff.dto.RecommendationDto;
import com.remelearning.bff.dto.WeakPointDto;
import com.remelearning.bff.service.LearnerOverviewService;
import com.remelearning.bff.service.WeakPointAggregationService;
import com.remelearning.common.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;

@Tag(name = "Learners", description = "Composite views for the UI, aggregated from the domain services")
@RestController
@RequestMapping("/api/v1/learners")
@RequiredArgsConstructor
public class LearnerController {

	private final LearnerOverviewService learnerOverviewService;
	private final WeakPointAggregationService weakPointAggregationService;
	private final RecommendationServiceClient recommendationServiceClient;

	@Operation(summary = "Composite learner overview: dashboard progress/recommendations + recent recordings, fanned out in parallel")
	@GetMapping("/{userId}/overview")
	public Mono<ApiResponse<LearnerOverviewResponse>> getOverview(@PathVariable String userId) {
		return learnerOverviewService.getOverview(userId).map(ApiResponse::ok);
	}

	@Operation(summary = "All of a learner's weak points, merged from english-service's vocabulary/grammar/pronunciation endpoints, keyed by category")
	@GetMapping("/{userId}/weak-points")
	public Mono<ApiResponse<Map<String, List<WeakPointDto>>>> getWeakPoints(@PathVariable String userId) {
		return weakPointAggregationService.getWeakPoints(userId).map(ApiResponse::ok);
	}

	@Operation(summary = "A learner's recommendations grouped by category; thin proxy to recommendation-service")
	@GetMapping("/{userId}/recommendations")
	public Mono<ApiResponse<Map<String, List<RecommendationDto>>>> getRecommendations(@PathVariable String userId) {
		return recommendationServiceClient.getGroupedRecommendations(userId).map(ApiResponse::ok);
	}
}
