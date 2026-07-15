package com.remelearning.recommendation.service.impl;

import com.remelearning.common.constants.KafkaTopics;
import com.remelearning.common.queue.EventPublisher;
import com.remelearning.recommendation.domain.Recommendation;
import com.remelearning.common.event.LearningGapAnalyzedEvent;
import com.remelearning.common.event.RecommendationGeneratedEvent;
import com.remelearning.common.event.RecommendationPayload;
import com.remelearning.common.event.WeakPointPayload;
import com.remelearning.recommendation.mapper.RecommendationMapper;
import com.remelearning.recommendation.service.RecommendationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class RecommendationServiceImpl implements RecommendationService {

	private final RecommendationMapper mapper;
	private final EventPublisher eventPublisher;

	// Unlike english-service's per-domain services, no category filtering here: every weak point
	// carried by the event (vocabulary/grammar/pronunciation) is upserted as its own recommendation
	// row, keyed on (userId, itemId) so re-analysis updates the score in place. Once all rows are
	// persisted, a recommendation.generated event is published carrying the full batch.
	@Override
	@Transactional
	public void handleLearningGapAnalyzed(LearningGapAnalyzedEvent event) {
		List<RecommendationPayload> payloads = event.getWeakPoints().stream()
				.map(weakPoint -> {
					mapper.upsert(toRecommendation(event, weakPoint));
					return toPayload(weakPoint);
				})
				.collect(Collectors.toList());

		RecommendationGeneratedEvent generatedEvent =
				new RecommendationGeneratedEvent(event.getRecordingId(), event.getUserId(), payloads);
		eventPublisher.publish(KafkaTopics.RECOMMENDATION_GENERATED, event.getUserId(), generatedEvent);
		log.info("Persisted {} recommendation(s) for user {} (recording {}) and published {}",
				payloads.size(), event.getUserId(), event.getRecordingId(), KafkaTopics.RECOMMENDATION_GENERATED);
	}

	@Override
	public List<Recommendation> getByUserId(String userId, String category) {
		// category is optional (null = no filter); mapper sorts by forgetting score desc.
		return mapper.findByUserId(userId, category);
	}

	@Override
	public Map<String, List<Recommendation>> getByUserIdGrouped(String userId) {
		// Fetch everything (no category filter) then group in-memory by category.
		return getByUserId(userId, null).stream()
				.collect(Collectors.groupingBy(Recommendation::getCategory));
	}

	/** Maps one weak point + its parent event into a persistable Recommendation row. */
	private Recommendation toRecommendation(LearningGapAnalyzedEvent event, WeakPointPayload weakPoint) {
		return Recommendation.builder()
				.userId(event.getUserId())
				.recordingId(event.getRecordingId())
				.itemId(weakPoint.getItemId())
				.category(weakPoint.getCategory())
				.label(weakPoint.getLabel())
				.forgettingScore(weakPoint.getForgettingScore())
				.recommendationText(weakPoint.getRecommendation())
				.build();
	}

	/** Maps one weak point into the outbound recommendation.generated event's payload shape. */
	private RecommendationPayload toPayload(WeakPointPayload weakPoint) {
		return RecommendationPayload.builder()
				.itemId(weakPoint.getItemId())
				.category(weakPoint.getCategory())
				.label(weakPoint.getLabel())
				.recommendationText(weakPoint.getRecommendation())
				.forgettingScore(weakPoint.getForgettingScore())
				.build();
	}
}
