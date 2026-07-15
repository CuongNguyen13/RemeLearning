package com.remelearning.dashboard.service.impl;

import com.remelearning.dashboard.domain.RecommendationSnapshot;
import com.remelearning.dashboard.domain.WeakPointSnapshot;
import com.remelearning.dashboard.dto.CategoryProgress;
import com.remelearning.dashboard.dto.DashboardSummaryResponse;
import com.remelearning.common.event.LearningGapAnalyzedEvent;
import com.remelearning.common.event.RecommendationGeneratedEvent;
import com.remelearning.common.event.RecommendationPayload;
import com.remelearning.common.event.WeakPointPayload;
import com.remelearning.dashboard.mapper.RecentRecommendationMapper;
import com.remelearning.dashboard.mapper.WeakPointSnapshotMapper;
import com.remelearning.dashboard.service.DashboardService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/** Default {@link DashboardService} backed by MyBatis mappers. */
@Slf4j
@Service
@RequiredArgsConstructor
public class DashboardServiceImpl implements DashboardService {

	private static final int RECENT_RECOMMENDATIONS_LIMIT = 10;

	private final WeakPointSnapshotMapper weakPointSnapshotMapper;
	private final RecentRecommendationMapper recentRecommendationMapper;

	// Unlike english-service's per-domain consumers, every category (grammar/vocabulary/
	// pronunciation) is kept here - no filtering - since this builds one unified cross-domain
	// snapshot. Each item is upserted keyed on (userId, itemId) so re-analysis updates in place.
	@Override
	@Transactional
	public void recordWeakPoint(LearningGapAnalyzedEvent event) {
		for (WeakPointPayload weakPoint : event.getWeakPoints()) {
			weakPointSnapshotMapper.upsert(WeakPointSnapshot.builder()
					.userId(event.getUserId())
					.recordingId(event.getRecordingId())
					.itemId(weakPoint.getItemId())
					.category(weakPoint.getCategory())
					.label(weakPoint.getLabel())
					.forgettingScore(weakPoint.getForgettingScore())
					.recommendation(weakPoint.getRecommendation())
					.build());
		}
	}

	// Upserts each recommendation in the event, keyed on (userId, itemId), so re-recommending
	// the same item refreshes it in place instead of duplicating rows.
	@Override
	@Transactional
	public void recordRecommendations(RecommendationGeneratedEvent event) {
		for (RecommendationPayload recommendation : event.getRecommendations()) {
			recentRecommendationMapper.upsert(RecommendationSnapshot.builder()
					.userId(event.getUserId())
					.itemId(recommendation.getItemId())
					.category(recommendation.getCategory())
					.label(recommendation.getLabel())
					.recommendationText(recommendation.getRecommendationText())
					.forgettingScore(recommendation.getForgettingScore())
					.build());
		}
	}

	// Combines a GROUP BY category rollup (computed at read time, not a running counter) with the
	// most recent recommendations into the single aggregate dashboard response.
	@Override
	public DashboardSummaryResponse getSummary(String userId) {
		List<CategoryProgress> categoryProgress = weakPointSnapshotMapper.selectProgressSummary(userId);
		List<RecommendationSnapshot> recentRecommendations =
				recentRecommendationMapper.findRecentByUserId(userId, RECENT_RECOMMENDATIONS_LIMIT);
		return new DashboardSummaryResponse(userId, categoryProgress, recentRecommendations);
	}
}
