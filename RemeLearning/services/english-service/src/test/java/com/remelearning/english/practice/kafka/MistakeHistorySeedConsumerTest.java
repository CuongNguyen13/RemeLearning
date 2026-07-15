package com.remelearning.english.practice.kafka;

import com.remelearning.common.event.EventCodec;
import com.remelearning.common.event.LearningGapAnalyzedEvent;
import com.remelearning.common.event.WeakPointPayload;
import com.remelearning.english.practice.domain.MistakeHistoryEntry;
import com.remelearning.english.practice.mapper.MistakeHistoryMapper;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

class MistakeHistorySeedConsumerTest {

	private final MistakeHistoryMapper mistakeHistoryMapper = mock(MistakeHistoryMapper.class);
	private final MistakeHistorySeedConsumer consumer = new MistakeHistorySeedConsumer(mistakeHistoryMapper);

	@Test
	void seedsHistoryForEveryWeakPointRegardlessOfCategory() throws Exception {
		LearningGapAnalyzedEvent event = new LearningGapAnalyzedEvent();
		event.setRecordingId("rec-1");
		event.setUserId("user-1");
		event.setWeakPoints(List.of(
				weakPoint("item-1", "vocabulary", "reluctant"),
				weakPoint("item-2", "grammar", "past tense")));

		consumer.onMessage(EventCodec.MAPPER.writeValueAsString(event));

		verify(mistakeHistoryMapper, times(2)).seedIfAbsent(org.mockito.ArgumentMatchers.any());
		verify(mistakeHistoryMapper).seedIfAbsent(eq(MistakeHistoryEntry.builder()
				.userId("user-1")
				.itemId("item-1")
				.category("vocabulary")
				.label("reluctant")
				.occurrenceCount(1)
				.build()));
	}

	@Test
	void malformedMessageIsLoggedNotThrown() {
		consumer.onMessage("not valid json");

		verify(mistakeHistoryMapper, never()).seedIfAbsent(org.mockito.ArgumentMatchers.any());
	}

	private WeakPointPayload weakPoint(String itemId, String category, String label) {
		WeakPointPayload payload = new WeakPointPayload();
		payload.setItemId(itemId);
		payload.setCategory(category);
		payload.setLabel(label);
		payload.setForgettingScore(0.5);
		payload.setRecommendation("Review " + label);
		return payload;
	}
}
