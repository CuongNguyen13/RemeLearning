package com.remelearning.dashboard.controller;

import com.remelearning.common.response.ApiResponse;
import com.remelearning.dashboard.dto.DashboardSummaryResponse;
import com.remelearning.dashboard.service.DashboardService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Dashboard", description = "Cross-domain learner progress dashboard, built purely from Kafka events")
@RestController
@RequestMapping("/api/v1/dashboard")
@RequiredArgsConstructor
public class DashboardController {

	private final DashboardService dashboardService;

	@Operation(summary = "Get a learner's aggregate dashboard: per-category weak-point counts/avg forgetting score "
			+ "(computed at read time via GROUP BY) plus the most recent recommendations")
	@GetMapping("/{userId}")
	public ApiResponse<DashboardSummaryResponse> getSummary(@PathVariable String userId) {
		return ApiResponse.ok(dashboardService.getSummary(userId));
	}
}
