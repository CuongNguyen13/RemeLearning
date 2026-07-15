package com.remelearning.dashboard.kafka;

import com.remelearning.common.constants.KafkaTopics;
import com.remelearning.common.event.EventCodec;
import com.remelearning.common.event.LearningGapAnalyzedEvent;
import com.remelearning.dashboard.service.DashboardService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Consumes {@code learning.gap.analyzed} (published by ai-service) into the cross-domain
 * dashboard snapshot. Runs on its own consumer groupId, distinct from english-service's
 * groupIds, so it receives every message on the topic rather than sharing partitions.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class LearningGapAnalyzedConsumer {

	private final DashboardService dashboardService;

	@KafkaListener(topics = KafkaTopics.LEARNING_GAP_ANALYZED, groupId = "dashboard-service")
	public void onMessage(String message) {
		// Decode ai-service's snake_case JSON (no envelope) and delegate to the service; any
		// failure is logged, not rethrown, so a malformed message can't wedge the consumer.
		try {
			LearningGapAnalyzedEvent event = EventCodec.MAPPER.readValue(message, LearningGapAnalyzedEvent.class);
			dashboardService.recordWeakPoint(event);
		} catch (Exception e) {
			log.error("Failed to process {} message: {}", KafkaTopics.LEARNING_GAP_ANALYZED, message, e);
		}
	}
}
