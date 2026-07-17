package com.remelearning.english.grammar.kafka;

import com.remelearning.common.constants.KafkaTopics;
import com.remelearning.common.event.EventCodec;
import com.remelearning.common.event.LearningGapAnalyzedEvent;
import com.remelearning.english.grammar.service.GrammarWeakPointService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component("grammarLearningGapAnalyzedConsumer")
@RequiredArgsConstructor
public class LearningGapAnalyzedConsumer {

	private final GrammarWeakPointService grammarWeakPointService;

	// A distinct groupId from vocabulary's consumer is required: two @KafkaListeners sharing one
	// consumer group on the same topic get partitions split between them (Kafka load-balances
	// within a group), so each would only see a subset of messages instead of every message.
	@KafkaListener(topics = KafkaTopics.LEARNING_GAP_ANALYZED, groupId = "${spring.application.name}-grammar")
	public void onMessage(String message) {
		try {
			LearningGapAnalyzedEvent event = EventCodec.MAPPER.readValue(message, LearningGapAnalyzedEvent.class);
			grammarWeakPointService.saveWeakPoints(event);
		} catch (Exception e) {
			log.error("Failed to process {} message: {}", KafkaTopics.LEARNING_GAP_ANALYZED, message, e);
		}
	}
}
