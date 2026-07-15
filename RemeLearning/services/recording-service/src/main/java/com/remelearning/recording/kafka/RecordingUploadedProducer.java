package com.remelearning.recording.kafka;

import com.remelearning.common.constants.KafkaTopics;
import com.remelearning.common.event.EventCodec;
import com.remelearning.recording.event.RecordingUploadedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

/** Publishes {@code recording.uploaded} once a recording's file has been stored in S3. */
@Slf4j
@Component
@RequiredArgsConstructor
public class RecordingUploadedProducer {

	private final KafkaTemplate<String, Object> kafkaTemplate;

	// Serializes the event to snake_case JSON (matching ai-service's pydantic schema) and sends
	// it keyed by recordingId so all messages for the same recording land on the same partition.
	public void publish(RecordingUploadedEvent event) {
		try {
			String payload = EventCodec.MAPPER.writeValueAsString(event);
			kafkaTemplate.send(KafkaTopics.RECORDING_UPLOADED, event.getRecordingId(), payload)
					.whenComplete((result, ex) -> {
						if (ex != null) {
							log.error("Failed to publish {} for recordingId={}",
									KafkaTopics.RECORDING_UPLOADED, event.getRecordingId(), ex);
						} else {
							log.debug("Published {} for recordingId={}",
									KafkaTopics.RECORDING_UPLOADED, event.getRecordingId());
						}
					});
		} catch (Exception e) {
			log.error("Failed to serialize {} for recordingId={}",
					KafkaTopics.RECORDING_UPLOADED, event.getRecordingId(), e);
		}
	}
}
