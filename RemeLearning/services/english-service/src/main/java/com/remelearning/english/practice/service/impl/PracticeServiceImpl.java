package com.remelearning.english.practice.service.impl;

import com.remelearning.common.event.AnalysisRequestedEvent;
import com.remelearning.common.event.MistakeHistoryItemPayload;
import com.remelearning.english.practice.domain.PracticeAttempt;
import com.remelearning.english.practice.dto.PracticeAttemptRequest;
import com.remelearning.english.practice.dto.PracticeRedoRequest;
import com.remelearning.english.practice.kafka.AnalysisRequestedProducer;
import com.remelearning.english.practice.mapper.MistakeHistoryMapper;
import com.remelearning.english.practice.mapper.PracticeAttemptMapper;
import com.remelearning.english.practice.service.PracticeService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class PracticeServiceImpl implements PracticeService {

	private final PracticeAttemptMapper practiceAttemptMapper;
	private final MistakeHistoryMapper mistakeHistoryMapper;
	private final AnalysisRequestedProducer analysisRequestedProducer;

	// Grades and logs every submitted answer, refreshes mistake_history for each item (occurrence
	// count only rises on a wrong answer, recency always refreshes), then bundles the learner's
	// full current history into one learning.gap.analysis.requested event so ai-service can
	// recompute forgetting scores - the same recurring-mistake pipeline a real recording feeds.
	@Override
	@Transactional
	public void redo(PracticeRedoRequest request) {
		String userId = request.getUserId();

		for (PracticeAttemptRequest attempt : request.getAttempts()) {
			practiceAttemptMapper.insert(PracticeAttempt.builder()
					.userId(userId)
					.itemId(attempt.getItemId())
					.category(attempt.getCategory())
					.label(attempt.getLabel())
					.correct(attempt.isCorrect())
					.build());

			mistakeHistoryMapper.recordAttempt(userId, attempt.getItemId(), attempt.getCategory(),
					attempt.getLabel(), attempt.isCorrect());
		}

		AnalysisRequestedEvent analysisRequestedEvent = AnalysisRequestedEvent.builder()
				.recordingId("practice-" + UUID.randomUUID())
				.userId(userId)
				.segments(Collections.emptyList())
				.history(buildHistoryPayload(userId))
				.build();
		analysisRequestedProducer.publish(analysisRequestedEvent);

		log.info("Graded {} redo attempt(s) for user {}, requested re-analysis of {} history item(s)",
				request.getAttempts().size(), userId, analysisRequestedEvent.getHistory().size());
	}

	private List<MistakeHistoryItemPayload> buildHistoryPayload(String userId) {
		Instant now = Instant.now();
		return mistakeHistoryMapper.findByUserId(userId).stream()
				.map(entry -> MistakeHistoryItemPayload.builder()
						.itemId(entry.getItemId())
						.category(entry.getCategory())
						.label(entry.getLabel())
						.occurrenceCount(entry.getOccurrenceCount())
						.lastSeenDaysAgo(Duration.between(entry.getLastSeenAt(), now).toSeconds() / 86400.0)
						.build())
				.toList();
	}
}
