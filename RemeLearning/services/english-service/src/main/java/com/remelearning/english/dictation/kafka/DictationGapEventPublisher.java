package com.remelearning.english.dictation.kafka;

import com.remelearning.common.constants.KafkaTopics;
import com.remelearning.common.event.EventCodec;
import com.remelearning.common.event.LearningGapAnalyzedEvent;
import com.remelearning.common.event.WeakPointPayload;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Publishes a learner's dictation misses onto {@code learning.gap.analyzed} - the same topic
 * ai-service emits - so the already-built pipeline (recommendation-service's ExerciseGenerator,
 * dashboard-service, english-service's own vocabulary consumer + MistakeHistorySeedConsumer) turns
 * them into study recommendations with no new analyzer. Serialized as plain snake_case JSON via
 * {@link EventCodec} to match how every consumer of this topic decodes it.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DictationGapEventPublisher {

	private final KafkaTemplate<String, Object> kafkaTemplate;

	// Builds and publishes the event keyed by userId; a serialization/publish failure is logged, not
	// rethrown, so it can't break grading an attempt (the recommendation feed is best-effort here).
	public void publish(String recordingId, String userId, List<WeakPointPayload> weakPoints) {
		if (weakPoints.isEmpty()) {
			return;
		}
		LearningGapAnalyzedEvent event = new LearningGapAnalyzedEvent();
		event.setRecordingId(recordingId);
		event.setUserId(userId);
		event.setWeakPoints(weakPoints);
		try {
			kafkaTemplate.send(KafkaTopics.LEARNING_GAP_ANALYZED, userId, EventCodec.MAPPER.writeValueAsString(event));
			log.info("Published dictation-derived learning.gap.analyzed for userId={} ({} weak points)",
					userId, weakPoints.size());
		} catch (Exception ex) {
			log.error("Failed to publish dictation-derived learning.gap.analyzed for userId={}", userId, ex);
		}
	}
}
