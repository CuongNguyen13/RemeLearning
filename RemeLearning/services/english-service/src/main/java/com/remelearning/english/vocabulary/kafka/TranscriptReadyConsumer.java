package com.remelearning.english.vocabulary.kafka;

import com.remelearning.common.constants.KafkaTopics;
import com.remelearning.common.event.EventCodec;
import com.remelearning.english.vocabulary.event.TranscriptReadyEvent;
import com.remelearning.english.vocabulary.service.TranscriptService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class TranscriptReadyConsumer {

	private final TranscriptService transcriptService;

	@KafkaListener(topics = KafkaTopics.TRANSCRIPT_READY, groupId = "${spring.application.name}")
	public void onMessage(String message) {
		try {
			TranscriptReadyEvent event = EventCodec.MAPPER.readValue(message, TranscriptReadyEvent.class);
			transcriptService.saveTranscript(event);
		} catch (Exception e) {
			log.error("Failed to process {} message: {}", KafkaTopics.TRANSCRIPT_READY, message, e);
		}
	}
}
