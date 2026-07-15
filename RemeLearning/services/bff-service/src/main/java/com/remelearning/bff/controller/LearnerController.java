package com.remelearning.bff.controller;

import com.remelearning.bff.client.EnglishServiceClient;
import com.remelearning.bff.client.RecommendationServiceClient;
import com.remelearning.bff.dto.LearnerOverviewResponse;
import com.remelearning.bff.dto.PracticeRedoRequestDto;
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
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
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
	private final EnglishServiceClient englishServiceClient;

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

	@Operation(summary = "The redo-exercise set: a learner's top weak items across all three domains, "
			+ "sorted by forgetting score desc, to present as the next exercise to redo")
	@GetMapping("/{userId}/practice/next")
	public Mono<ApiResponse<List<WeakPointDto>>> getNextPracticeSet(
			@PathVariable String userId,
			@RequestParam(required = false, defaultValue = "10") int limit) {
		return weakPointAggregationService.getNextPracticeSet(userId, limit).map(ApiResponse::ok);
	}

	@Operation(summary = "Submit the graded results of a learner redoing an exercise; thin proxy to "
			+ "english-service, which refreshes mistake history and re-triggers forgetting-score analysis")
	@PostMapping("/{userId}/practice/redo")
	public Mono<ApiResponse<Void>> redoPractice(@PathVariable String userId, @RequestBody PracticeRedoRequestDto request) {
		request.setUserId(userId);
		return englishServiceClient.redoPractice(request);
	}
}
