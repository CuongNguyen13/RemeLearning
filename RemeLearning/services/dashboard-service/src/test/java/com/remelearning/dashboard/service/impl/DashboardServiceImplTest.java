package com.remelearning.dashboard.service.impl;

import com.remelearning.dashboard.domain.RecommendationSnapshot;
import com.remelearning.dashboard.dto.CategoryProgress;
import com.remelearning.dashboard.dto.DashboardSummaryResponse;
import com.remelearning.common.event.LearningGapAnalyzedEvent;
import com.remelearning.common.event.RecommendationGeneratedEvent;
import com.remelearning.common.event.RecommendationPayload;
import com.remelearning.common.event.WeakPointPayload;
import com.remelearning.dashboard.mapper.RecentRecommendationMapper;
import com.remelearning.dashboard.mapper.WeakPointSnapshotMapper;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DashboardServiceImplTest {

	private final WeakPointSnapshotMapper weakPointSnapshotMapper = mock(WeakPointSnapshotMapper.class);
	private final RecentRecommendationMapper recentRecommendationMapper = mock(RecentRecommendationMapper.class);
	private final DashboardServiceImpl service =
			new DashboardServiceImpl(weakPointSnapshotMapper, recentRecommendationMapper);

	@Test
	void getSummaryAssemblesMapperResultsIntoDashboardSummaryResponse() {
		List<CategoryProgress> progress = List.of(
				new CategoryProgress("vocabulary", 3L, 0.42, Instant.parse("2026-07-14T00:00:00Z")),
				new CategoryProgress("grammar", 1L, 0.9, Instant.parse("2026-07-13T00:00:00Z")));
		List<RecommendationSnapshot> recommendations = List.of(
				RecommendationSnapshot.builder().userId("user-1").itemId("item-1").category("vocabulary")
						.label("reluctant").recommendationText("Review synonyms").forgettingScore(0.42).build());
		when(weakPointSnapshotMapper.selectProgressSummary("user-1")).thenReturn(progress);
		when(recentRecommendationMapper.findRecentByUserId("user-1", 10)).thenReturn(recommendations);

		DashboardSummaryResponse summary = service.getSummary("user-1");

		assertThat(summary).isEqualTo(new DashboardSummaryResponse("user-1", progress, recommendations));
	}

	@Test
	void recordWeakPointUpsertsEveryWeakPointInTheEvent() {
		LearningGapAnalyzedEvent event = new LearningGapAnalyzedEvent();
		event.setRecordingId("rec-1");
		event.setUserId("user-1");
		event.setWeakPoints(List.of(
				weakPoint("vocabulary", "item-1"),
				weakPoint("grammar", "item-2"),
				weakPoint("pronunciation", "item-3")));

		service.recordWeakPoint(event);

		// Unlike english-service's per-domain consumers, dashboard-service keeps every category.
		verify(weakPointSnapshotMapper, times(3)).upsert(any());
	}

	@Test
	void recordRecommendationsUpsertsEveryRecommendationInTheEvent() {
		RecommendationGeneratedEvent event = new RecommendationGeneratedEvent();
		event.setEventId("evt-1");
		event.setEventType("recommendation.generated");
		event.setOccurredAt(Instant.now());
		event.setRecordingId("rec-1");
		event.setUserId("user-1");
		event.setRecommendations(List.of(
				recommendation("vocabulary", "item-1"),
				recommendation("grammar", "item-2")));

		service.recordRecommendations(event);

		verify(recentRecommendationMapper, times(2)).upsert(any());
	}

	private WeakPointPayload weakPoint(String category, String itemId) {
		WeakPointPayload payload = new WeakPointPayload();
		payload.setItemId(itemId);
		payload.setCategory(category);
		payload.setLabel("label-" + itemId);
		payload.setForgettingScore(0.5);
		payload.setRecommendation("Review " + itemId);
		return payload;
	}

	private RecommendationPayload recommendation(String category, String itemId) {
		RecommendationPayload payload = new RecommendationPayload();
		payload.setItemId(itemId);
		payload.setCategory(category);
		payload.setLabel("label-" + itemId);
		payload.setRecommendationText("Review " + itemId);
		payload.setForgettingScore(0.5);
		return payload;
	}
}
