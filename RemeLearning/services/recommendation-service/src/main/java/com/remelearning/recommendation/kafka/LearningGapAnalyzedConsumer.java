package com.remelearning.recommendation.kafka;

import com.remelearning.common.constants.KafkaTopics;
import com.remelearning.common.event.EventCodec;
import com.remelearning.common.event.LearningGapAnalyzedEvent;
import com.remelearning.recommendation.service.RecommendationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Listens on {@code learning.gap.analyzed} - the same event english-service's vocabulary/grammar/
 * pronunciation domains consume - on its own distinct groupId ("recommendation-service") so Kafka
 * delivers a full copy of every message here instead of splitting partitions with the other
 * consumer groups on the same topic.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class LearningGapAnalyzedConsumer {

	private final RecommendationService recommendationService;

	@KafkaListener(topics = KafkaTopics.LEARNING_GAP_ANALYZED, groupId = "recommendation-service")
	public void onMessage(String message) {
		try {
			// Decode ai-service's snake_case JSON and delegate to the service; exceptions are
			// caught and logged here (not rethrown) so a bad message doesn't stall the consumer.
			LearningGapAnalyzedEvent event = EventCodec.MAPPER.readValue(message, LearningGapAnalyzedEvent.class);
			recommendationService.handleLearningGapAnalyzed(event);
		} catch (Exception e) {
			log.error("Failed to process {} message: {}", KafkaTopics.LEARNING_GAP_ANALYZED, message, e);
		}
	}
}
