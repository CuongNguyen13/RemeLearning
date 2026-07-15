package com.remelearning.english.practice.service;

import com.remelearning.common.event.AnalysisRequestedEvent;
import com.remelearning.common.event.MistakeHistoryItemPayload;
import com.remelearning.english.practice.domain.MistakeHistoryEntry;
import com.remelearning.english.practice.domain.PracticeAttempt;
import com.remelearning.english.practice.dto.PracticeAttemptRequest;
import com.remelearning.english.practice.dto.PracticeRedoRequest;
import com.remelearning.english.practice.kafka.AnalysisRequestedProducer;
import com.remelearning.english.practice.mapper.MistakeHistoryMapper;
import com.remelearning.english.practice.mapper.PracticeAttemptMapper;
import com.remelearning.english.practice.service.impl.PracticeServiceImpl;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PracticeServiceImplTest {

	private final PracticeAttemptMapper practiceAttemptMapper = mock(PracticeAttemptMapper.class);
	private final MistakeHistoryMapper mistakeHistoryMapper = mock(MistakeHistoryMapper.class);
	private final AnalysisRequestedProducer analysisRequestedProducer = mock(AnalysisRequestedProducer.class);
	private final PracticeServiceImpl service =
			new PracticeServiceImpl(practiceAttemptMapper, mistakeHistoryMapper, analysisRequestedProducer);

	@Test
	void gradesEachAttemptLogsItAndRefreshesMistakeHistory() {
		PracticeRedoRequest request = redoRequest(
				attempt("item-1", "vocabulary", "reluctant", false),
				attempt("item-2", "grammar", "past tense", true));
		when(mistakeHistoryMapper.findByUserId("user-1")).thenReturn(List.of());

		service.redo(request);

		ArgumentCaptor<PracticeAttempt> attemptCaptor = ArgumentCaptor.forClass(PracticeAttempt.class);
		verify(practiceAttemptMapper, times(2)).insert(attemptCaptor.capture());
		assertThat(attemptCaptor.getAllValues())
				.extracting(PracticeAttempt::getItemId, PracticeAttempt::isCorrect)
				.containsExactly(org.assertj.core.groups.Tuple.tuple("item-1", false),
						org.assertj.core.groups.Tuple.tuple("item-2", true));

		verify(mistakeHistoryMapper).recordAttempt("user-1", "item-1", "vocabulary", "reluctant", false);
		verify(mistakeHistoryMapper).recordAttempt("user-1", "item-2", "grammar", "past tense", true);
	}

	@Test
	void publishesOneAnalysisRequestedEventWithTheUsersFullCurrentHistory() {
		PracticeRedoRequest request = redoRequest(attempt("item-1", "vocabulary", "reluctant", false));
		Instant threeDaysAgo = Instant.now().minus(3, ChronoUnit.DAYS);
		when(mistakeHistoryMapper.findByUserId("user-1")).thenReturn(List.of(
				MistakeHistoryEntry.builder()
						.userId("user-1")
						.itemId("item-1")
						.category("vocabulary")
						.label("reluctant")
						.occurrenceCount(4)
						.lastSeenAt(threeDaysAgo)
						.build()));

		service.redo(request);

		ArgumentCaptor<AnalysisRequestedEvent> eventCaptor = ArgumentCaptor.forClass(AnalysisRequestedEvent.class);
		verify(analysisRequestedProducer).publish(eventCaptor.capture());
		AnalysisRequestedEvent event = eventCaptor.getValue();

		assertThat(event.getUserId()).isEqualTo("user-1");
		assertThat(event.getRecordingId()).startsWith("practice-");
		assertThat(event.getSegments()).isEmpty();
		assertThat(event.getHistory()).hasSize(1);
		MistakeHistoryItemPayload historyItem = event.getHistory().get(0);
		assertThat(historyItem.getItemId()).isEqualTo("item-1");
		assertThat(historyItem.getOccurrenceCount()).isEqualTo(4);
		assertThat(historyItem.getLastSeenDaysAgo()).isCloseTo(3.0, within(0.01));
	}

	private PracticeRedoRequest redoRequest(PracticeAttemptRequest... attempts) {
		PracticeRedoRequest request = new PracticeRedoRequest();
		request.setUserId("user-1");
		request.setAttempts(List.of(attempts));
		return request;
	}

	private PracticeAttemptRequest attempt(String itemId, String category, String label, boolean correct) {
		PracticeAttemptRequest attempt = new PracticeAttemptRequest();
		attempt.setItemId(itemId);
		attempt.setCategory(category);
		attempt.setLabel(label);
		attempt.setCorrect(correct);
		return attempt;
	}
}
