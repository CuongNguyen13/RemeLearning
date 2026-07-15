package com.remelearning.english.practice.kafka;

import com.remelearning.common.constants.KafkaTopics;
import com.remelearning.common.event.AnalysisRequestedEvent;
import com.remelearning.common.event.EventCodec;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

/**
 * Publishes {@code learning.gap.analysis.requested} once a learner's redo-exercise attempts
 * have been graded and their mistake history refreshed, asking ai-service to recompute
 * forgetting scores from the updated history.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AnalysisRequestedProducer {

	private final KafkaTemplate<String, Object> kafkaTemplate;

	// Serializes to snake_case JSON (matching ai-service's pydantic schema), keyed by recordingId
	// so all messages for the same re-analysis batch land on the same partition.
	public void publish(AnalysisRequestedEvent event) {
		try {
			String payload = EventCodec.MAPPER.writeValueAsString(event);
			kafkaTemplate.send(KafkaTopics.LEARNING_GAP_ANALYSIS_REQUESTED, event.getRecordingId(), payload)
					.whenComplete((result, ex) -> {
						if (ex != null) {
							log.error("Failed to publish {} for userId={}",
									KafkaTopics.LEARNING_GAP_ANALYSIS_REQUESTED, event.getUserId(), ex);
						} else {
							log.debug("Published {} for userId={} with {} history item(s)",
									KafkaTopics.LEARNING_GAP_ANALYSIS_REQUESTED, event.getUserId(), event.getHistory().size());
						}
					});
		} catch (Exception e) {
			log.error("Failed to serialize {} for userId={}",
					KafkaTopics.LEARNING_GAP_ANALYSIS_REQUESTED, event.getUserId(), e);
		}
	}
}
