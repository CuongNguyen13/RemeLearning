package com.remelearning.english.vocabulary.kafka;

import com.remelearning.common.constants.KafkaTopics;
import com.remelearning.common.event.EventCodec;
import com.remelearning.common.event.LearningGapAnalyzedEvent;
import com.remelearning.english.vocabulary.service.VocabularyWeakPointService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component("vocabularyLearningGapAnalyzedConsumer")
@RequiredArgsConstructor
public class LearningGapAnalyzedConsumer {

	private final VocabularyWeakPointService vocabularyWeakPointService;

	@KafkaListener(topics = KafkaTopics.LEARNING_GAP_ANALYZED, groupId = "${spring.application.name}")
	public void onMessage(String message) {
		try {
			LearningGapAnalyzedEvent event = EventCodec.MAPPER.readValue(message, LearningGapAnalyzedEvent.class);
			vocabularyWeakPointService.saveWeakPoints(event);
		} catch (Exception e) {
			log.error("Failed to process {} message: {}", KafkaTopics.LEARNING_GAP_ANALYZED, message, e);
		}
	}
}
