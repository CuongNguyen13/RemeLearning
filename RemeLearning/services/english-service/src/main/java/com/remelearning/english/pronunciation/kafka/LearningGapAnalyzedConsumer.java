package com.remelearning.english.pronunciation.kafka;

import com.remelearning.common.constants.KafkaTopics;
import com.remelearning.english.pronunciation.event.EventCodec;
import com.remelearning.english.pronunciation.event.LearningGapAnalyzedEvent;
import com.remelearning.english.pronunciation.service.PronunciationWeakPointService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class LearningGapAnalyzedConsumer {

	private final PronunciationWeakPointService pronunciationWeakPointService;

	// A distinct groupId from vocabulary's/grammar's consumers is required: two @KafkaListeners
	// sharing one consumer group on the same topic get partitions split between them (Kafka
	// load-balances within a group), so each would only see a subset of messages instead of every
	// message.
	@KafkaListener(topics = KafkaTopics.LEARNING_GAP_ANALYZED, groupId = "${spring.application.name}-pronunciation")
	public void onMessage(String message) {
		try {
			LearningGapAnalyzedEvent event = EventCodec.MAPPER.readValue(message, LearningGapAnalyzedEvent.class);
			pronunciationWeakPointService.saveWeakPoints(event);
		} catch (Exception e) {
			log.error("Failed to process {} message: {}", KafkaTopics.LEARNING_GAP_ANALYZED, message, e);
		}
	}
}
