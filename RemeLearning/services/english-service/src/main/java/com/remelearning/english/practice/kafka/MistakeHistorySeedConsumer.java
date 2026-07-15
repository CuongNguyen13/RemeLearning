package com.remelearning.english.practice.kafka;

import com.remelearning.common.constants.KafkaTopics;
import com.remelearning.common.event.EventCodec;
import com.remelearning.common.event.LearningGapAnalyzedEvent;
import com.remelearning.common.event.WeakPointPayload;
import com.remelearning.english.practice.domain.MistakeHistoryEntry;
import com.remelearning.english.practice.mapper.MistakeHistoryMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Seeds {@code mistake_history} the first time any item (any category) is ever surfaced by
 * {@code learning.gap.analyzed}, so history exists before a learner has redone anything even
 * once. Unlike the three domain consumers, this one keeps every category (own {@code groupId},
 * same pattern as recommendation-service/dashboard-service). Seeding is a no-op if the item
 * already has history — only graded redo attempts ({@link com.remelearning.english.practice.service.PracticeService})
 * update it afterward, so this never double-counts occurrences.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MistakeHistorySeedConsumer {

	private final MistakeHistoryMapper mistakeHistoryMapper;

	@KafkaListener(topics = KafkaTopics.LEARNING_GAP_ANALYZED, groupId = "${spring.application.name}-practice")
	@Transactional
	public void onMessage(String message) {
		try {
			LearningGapAnalyzedEvent event = EventCodec.MAPPER.readValue(message, LearningGapAnalyzedEvent.class);
			for (WeakPointPayload weakPoint : event.getWeakPoints()) {
				mistakeHistoryMapper.seedIfAbsent(MistakeHistoryEntry.builder()
						.userId(event.getUserId())
						.itemId(weakPoint.getItemId())
						.category(weakPoint.getCategory())
						.label(weakPoint.getLabel())
						.occurrenceCount(1)
						.build());
			}
		} catch (Exception e) {
			log.error("Failed to process {} message: {}", KafkaTopics.LEARNING_GAP_ANALYZED, message, e);
		}
	}
}
