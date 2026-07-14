package com.remelearning.english.vocabulary.event;

import lombok.Data;

import java.util.List;

/**
 * Mirrors ai-service's {@code app.schemas.events.TranscriptReadyEvent}, published to
 * {@link com.remelearning.common.constants.KafkaTopics#TRANSCRIPT_READY}.
 */
@Data
public class TranscriptReadyEvent {
	private String recordingId;
	private String userId;
	private String fullText;
	private List<SegmentPayload> segments;
}
