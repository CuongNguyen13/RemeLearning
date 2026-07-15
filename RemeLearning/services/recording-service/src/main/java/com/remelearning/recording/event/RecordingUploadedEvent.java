package com.remelearning.recording.event;

import lombok.Builder;
import lombok.Data;

/**
 * Mirrors ai-service's {@code app.schemas.events.RecordingUploadedEvent}, published to
 * {@link com.remelearning.common.constants.KafkaTopics#RECORDING_UPLOADED} once a recording's
 * file has landed in S3. Java fields stay camelCase — {@link EventCodec}'s snake_case mapper
 * handles the translation to the JSON shape ai-service expects.
 */
@Data
@Builder
public class RecordingUploadedEvent {
	private String recordingId;
	private String userId;
	private String s3Bucket;
	private String s3Key;
	private String languageCode;
}
