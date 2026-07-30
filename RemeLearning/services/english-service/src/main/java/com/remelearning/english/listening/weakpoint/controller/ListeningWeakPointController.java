package com.remelearning.english.listening.weakpoint.controller;

import com.remelearning.common.response.ApiResponse;
import com.remelearning.english.listening.weakpoint.domain.ListeningSourceType;
import com.remelearning.english.listening.weakpoint.domain.ListeningWeakPoint;
import com.remelearning.english.listening.weakpoint.service.ListeningWeakPointService;
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
import java.util.stream.Collectors;

@Tag(name = "Listening Weak Points", description = "Recurring/forgotten listening weak points, merged from "
		+ "dictation misses (dual-written) and listening-comprehension redo scoring")
@RestController
@RequestMapping("/api/v1/listening/weak-points")
@RequiredArgsConstructor
public class ListeningWeakPointController {

	private final ListeningWeakPointService listeningWeakPointService;

	@Operation(summary = "List a learner's recurring listening weak points, optionally filtered by source "
			+ "(DICTATION, COMPREHENSION), sorted by forgetting score desc")
	@GetMapping("/{userId}")
	public ApiResponse<List<ListeningWeakPoint>> getByUser(
			@PathVariable String userId,
			@RequestParam(required = false) ListeningSourceType sourceType) {
		return ApiResponse.ok(listeningWeakPointService.getWeakPoints(userId, sourceType));
	}

	@Operation(summary = "Same as GET /{userId}, grouped by source type")
	@GetMapping("/{userId}/grouped")
	public ApiResponse<Map<ListeningSourceType, List<ListeningWeakPoint>>> getByUserGrouped(@PathVariable String userId) {
		List<ListeningWeakPoint> all = listeningWeakPointService.getWeakPoints(userId, null);
		Map<ListeningSourceType, List<ListeningWeakPoint>> grouped = all.stream()
				.collect(Collectors.groupingBy(ListeningWeakPoint::getSourceType));
		return ApiResponse.ok(grouped);
	}
}
