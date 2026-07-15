package com.remelearning.dashboard.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.remelearning.common.constants.KafkaTopics;
import com.remelearning.common.event.RecommendationGeneratedEvent;
import com.remelearning.dashboard.service.DashboardService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Consumes {@code recommendation.generated} (published Java-to-Java by recommendation-service
 * via common's EventPublisher/BaseEvent infra) into the recent-recommendations snapshot. Unlike
 * learning.gap.analyzed, this event is plain camelCase JSON with an envelope, so it's decoded
 * with a dedicated camelCase ObjectMapper (registered with JavaTimeModule for the Instant
 * envelope field) instead of the snake_case EventCodec.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RecommendationGeneratedConsumer {

	private static final ObjectMapper MAPPER = new ObjectMapper().registerModule(new JavaTimeModule());

	private final DashboardService dashboardService;

	@KafkaListener(topics = KafkaTopics.RECOMMENDATION_GENERATED, groupId = "dashboard-service")
	public void onMessage(String message) {
		// Decode the camelCase envelope+payload and delegate to the service; any failure is
		// logged, not rethrown, so a malformed message can't wedge the consumer.
		try {
			RecommendationGeneratedEvent event = MAPPER.readValue(message, RecommendationGeneratedEvent.class);
			dashboardService.recordRecommendations(event);
		} catch (Exception e) {
			log.error("Failed to process {} message: {}", KafkaTopics.RECOMMENDATION_GENERATED, message, e);
		}
	}
}
