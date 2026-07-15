package com.remelearning.recommendation.controller;

import com.remelearning.common.response.ApiResponse;
import com.remelearning.recommendation.domain.Recommendation;
import com.remelearning.recommendation.service.RecommendationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@Tag(name = "Recommendations", description = "Personalized study recommendations derived from a learner's recurring/forgotten weak points across all categories")
@RestController
@RequestMapping("/api/v1/recommendations")
@RequiredArgsConstructor
public class RecommendationController {

	private final RecommendationService recommendationService;

	@Operation(summary = "List a learner's recommendations, optionally filtered by category (vocabulary, grammar, pronunciation), sorted by forgetting score desc")
	@GetMapping("/{userId}")
	public ApiResponse<List<Recommendation>> getByUser(
			@PathVariable String userId,
			@RequestParam(required = false) String category) {
		return ApiResponse.ok(recommendationService.getByUserId(userId, category));
	}

	@Operation(summary = "Same as GET /{userId}, grouped by category")
	@GetMapping("/{userId}/grouped")
	public ApiResponse<Map<String, List<Recommendation>>> getByUserGrouped(@PathVariable String userId) {
		return ApiResponse.ok(recommendationService.getByUserIdGrouped(userId));
	}
}
