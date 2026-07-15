package com.remelearning.common.event;

import lombok.Data;

import java.util.List;

/**
 * Mirrors ai-service's {@code app.schemas.events.LearningGapAnalyzedEvent}, published to
 * {@link com.remelearning.common.constants.KafkaTopics#LEARNING_GAP_ANALYZED}.
 */
@Data
public class LearningGapAnalyzedEvent {
	private String recordingId;
	private String userId;
	private List<WeakPointPayload> weakPoints;
}
