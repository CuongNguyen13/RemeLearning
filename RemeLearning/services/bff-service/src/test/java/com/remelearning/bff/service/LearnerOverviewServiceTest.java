package com.remelearning.bff.service;

import com.remelearning.bff.client.DashboardServiceClient;
import com.remelearning.bff.client.RecordingServiceClient;
import com.remelearning.bff.client.UserServiceClient;
import com.remelearning.bff.dto.CategoryProgressDto;
import com.remelearning.bff.dto.DashboardSummaryDto;
import com.remelearning.bff.dto.LearnerOverviewResponse;
import com.remelearning.bff.dto.RecordingDto;
import com.remelearning.bff.dto.RecommendationSnapshotDto;
import com.remelearning.bff.dto.UserDto;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class LearnerOverviewServiceTest {

	private final DashboardServiceClient dashboardServiceClient = mock(DashboardServiceClient.class);
	private final RecordingServiceClient recordingServiceClient = mock(RecordingServiceClient.class);
	private final UserServiceClient userServiceClient = mock(UserServiceClient.class);
	private final LearnerOverviewService service =
			new LearnerOverviewService(dashboardServiceClient, recordingServiceClient, userServiceClient);

	@Test
	void assemblesOverviewFromAllThreeDownstreamsWhenAllSucceed() {
		DashboardSummaryDto summary = new DashboardSummaryDto();
		summary.setUserId("user-1");
		summary.setCategoryProgress(List.of(new CategoryProgressDto()));
		summary.setRecentRecommendations(List.of(new RecommendationSnapshotDto()));
		RecordingDto recording = new RecordingDto();
		recording.setRecordingId("rec-1");
		UserDto user = new UserDto();
		user.setUserId("user-1");
		user.setEmail("learner@example.com");

		when(dashboardServiceClient.getSummary("user-1")).thenReturn(Mono.just(summary));
		when(recordingServiceClient.getRecordingsByUser("user-1")).thenReturn(Mono.just(List.of(recording)));
		when(userServiceClient.getByUserId("user-1")).thenReturn(Mono.just(user));

		StepVerifier.create(service.getOverview("user-1"))
				.assertNext(overview -> {
					assertThat(overview.getUserId()).isEqualTo("user-1");
					assertThat(overview.getCategoryProgress()).hasSize(1);
					assertThat(overview.getRecentRecommendations()).hasSize(1);
					assertThat(overview.getRecentRecordings()).containsExactly(recording);
					assertThat(overview.getUser()).isEqualTo(user);
				})
				.verifyComplete();
	}

	@Test
	void defaultsDashboardSliceToEmptyWhenDashboardServiceFails() {
		RecordingDto recording = new RecordingDto();
		recording.setRecordingId("rec-1");
		UserDto user = new UserDto();
		user.setUserId("user-1");

		when(dashboardServiceClient.getSummary("user-1")).thenReturn(Mono.error(new RuntimeException("dashboard down")));
		when(recordingServiceClient.getRecordingsByUser("user-1")).thenReturn(Mono.just(List.of(recording)));
		when(userServiceClient.getByUserId("user-1")).thenReturn(Mono.just(user));

		StepVerifier.create(service.getOverview("user-1"))
				.assertNext((LearnerOverviewResponse overview) -> {
					assertThat(overview.getCategoryProgress()).isEmpty();
					assertThat(overview.getRecentRecommendations()).isEmpty();
					assertThat(overview.getRecentRecordings()).containsExactly(recording);
					assertThat(overview.getUser()).isEqualTo(user);
				})
				.verifyComplete();
	}

	@Test
	void defaultsRecordingsSliceToEmptyWhenRecordingServiceFails() {
		DashboardSummaryDto summary = new DashboardSummaryDto();
		summary.setUserId("user-1");
		summary.setCategoryProgress(List.of(new CategoryProgressDto()));
		summary.setRecentRecommendations(List.of());
		UserDto user = new UserDto();
		user.setUserId("user-1");

		when(dashboardServiceClient.getSummary("user-1")).thenReturn(Mono.just(summary));
		when(recordingServiceClient.getRecordingsByUser("user-1")).thenReturn(Mono.error(new RuntimeException("recording down")));
		when(userServiceClient.getByUserId("user-1")).thenReturn(Mono.just(user));

		StepVerifier.create(service.getOverview("user-1"))
				.assertNext(overview -> {
					assertThat(overview.getCategoryProgress()).hasSize(1);
					assertThat(overview.getRecentRecordings()).isEmpty();
					assertThat(overview.getUser()).isEqualTo(user);
				})
				.verifyComplete();
	}

	@Test
	void defaultsUserSliceToEmptyWhenUserServiceFails() {
		DashboardSummaryDto summary = new DashboardSummaryDto();
		summary.setUserId("user-1");
		summary.setCategoryProgress(List.of(new CategoryProgressDto()));
		summary.setRecentRecommendations(List.of());
		RecordingDto recording = new RecordingDto();
		recording.setRecordingId("rec-1");

		when(dashboardServiceClient.getSummary("user-1")).thenReturn(Mono.just(summary));
		when(recordingServiceClient.getRecordingsByUser("user-1")).thenReturn(Mono.just(List.of(recording)));
		when(userServiceClient.getByUserId("user-1")).thenReturn(Mono.error(new RuntimeException("user-service down")));

		StepVerifier.create(service.getOverview("user-1"))
				.assertNext(overview -> {
					assertThat(overview.getCategoryProgress()).hasSize(1);
					assertThat(overview.getRecentRecordings()).containsExactly(recording);
					assertThat(overview.getUser()).isNull();
				})
				.verifyComplete();
	}
}
