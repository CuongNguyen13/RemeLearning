package com.remelearning.bff.service;

import com.remelearning.bff.client.DashboardServiceClient;
import com.remelearning.bff.client.RecordingServiceClient;
import com.remelearning.bff.client.UserServiceClient;
import com.remelearning.bff.dto.DashboardSummaryDto;
import com.remelearning.bff.dto.LearnerOverviewResponse;
import com.remelearning.bff.dto.RecordingDto;
import com.remelearning.bff.dto.UserDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

/**
 * Builds the composite "learner overview" payload the UI needs in one call: dashboard-service's
 * progress/recommendation summary, recording-service's recent recordings, and user-service's
 * profile, fanned out in parallel via {@link Mono#zip}. Any downstream being unavailable degrades
 * that slice to an empty default instead of failing the whole response.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LearnerOverviewService {

	private final DashboardServiceClient dashboardServiceClient;
	private final RecordingServiceClient recordingServiceClient;
	private final UserServiceClient userServiceClient;

	/** Fans out to dashboard-service, recording-service and user-service in parallel and assembles the combined overview. */
	public Mono<LearnerOverviewResponse> getOverview(String userId) {
		Mono<DashboardSummaryDto> dashboardMono = dashboardServiceClient.getSummary(userId)
				.onErrorResume(ex -> {
					log.warn("dashboard-service unavailable for userId={}, defaulting to empty summary", userId, ex);
					return Mono.just(emptyDashboardSummary(userId));
				});

		Mono<List<RecordingDto>> recordingsMono = recordingServiceClient.getRecordingsByUser(userId)
				.onErrorResume(ex -> {
					log.warn("recording-service unavailable for userId={}, defaulting to empty recordings list", userId, ex);
					return Mono.just(Collections.emptyList());
				});

		// Wrapped in Optional so a failed/absent lookup can still emit a value for Mono.zip (which
		// otherwise completes empty, not with a null, if any source is empty) - unwrapped again below.
		Mono<Optional<UserDto>> userMono = userServiceClient.getByUserId(userId)
				.map(Optional::ofNullable)
				.onErrorResume(ex -> {
					log.warn("user-service unavailable for userId={}, defaulting to empty user", userId, ex);
					return Mono.just(Optional.empty());
				});

		return Mono.zip(dashboardMono, recordingsMono, userMono)
				.map(tuple -> LearnerOverviewResponse.builder()
						.userId(userId)
						.categoryProgress(tuple.getT1().getCategoryProgress())
						.recentRecommendations(tuple.getT1().getRecentRecommendations())
						.recentRecordings(tuple.getT2())
						.user(tuple.getT3().orElse(null))
						.build());
	}

	// Fallback used when dashboard-service can't be reached, so downstream code never sees a null summary.
	private DashboardSummaryDto emptyDashboardSummary(String userId) {
		DashboardSummaryDto summary = new DashboardSummaryDto();
		summary.setUserId(userId);
		summary.setCategoryProgress(Collections.emptyList());
		summary.setRecentRecommendations(Collections.emptyList());
		return summary;
	}
}
