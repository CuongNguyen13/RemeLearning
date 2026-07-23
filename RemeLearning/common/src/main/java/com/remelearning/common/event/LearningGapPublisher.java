package com.remelearning.common.event;

import com.remelearning.common.constants.KafkaTopics;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Shared publisher for {@code learning.gap.analyzed}, generalizing the pattern first written for
 * dictation ({@code english.dictation.kafka.DictationGapEventPublisher}) so every "Học &amp; Luyện
 * tập với AI" skill (vocabulary/grammar/listening/speaking) feeds the same already-built downstream
 * pipeline (recommendation-service, dashboard-service, english-service's own per-domain consumers)
 * without a bespoke publisher per skill. Serialized as plain snake_case JSON via {@link EventCodec}
 * to match how every consumer of this topic decodes it - same wire format ai-service produces.
 * Dictation's own publisher is left untouched (not migrated onto this class) to avoid touching a
 * working, already-tested code path; new skills should call this one directly.
 */
@Slf4j
@Component
@ConditionalOnBean(KafkaTemplate.class)
@RequiredArgsConstructor
public class LearningGapPublisher {

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
			log.info("Published learning.gap.analyzed for userId={} ({} weak points)", userId, weakPoints.size());
		} catch (Exception ex) {
			log.error("Failed to publish learning.gap.analyzed for userId={}", userId, ex);
		}
	}
}
