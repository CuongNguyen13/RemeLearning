package com.remelearning.english.practice.service.impl;

import com.remelearning.common.event.AnalysisRequestedEvent;
import com.remelearning.common.event.MistakeHistoryItemPayload;
import com.remelearning.common.scoring.LabelKeys;
import com.remelearning.common.scoring.PopulationStats;
import com.remelearning.english.practice.domain.PracticeAttempt;
import com.remelearning.english.practice.dto.PracticeAttemptRequest;
import com.remelearning.english.practice.dto.PracticeRedoRequest;
import com.remelearning.english.practice.dto.ReviewQueueItem;
import com.remelearning.english.practice.kafka.AnalysisRequestedProducer;
import com.remelearning.english.practice.mapper.ItemDifficultyStatsMapper;
import com.remelearning.english.practice.mapper.MistakeHistoryMapper;
import com.remelearning.english.practice.mapper.PracticeAttemptMapper;
import com.remelearning.english.practice.scoring.WeakPointScoringOrchestrator;
import com.remelearning.english.practice.service.PracticeService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class PracticeServiceImpl implements PracticeService {

	private final PracticeAttemptMapper practiceAttemptMapper;
	private final MistakeHistoryMapper mistakeHistoryMapper;
	private final ItemDifficultyStatsMapper itemDifficultyStatsMapper;
	private final AnalysisRequestedProducer analysisRequestedProducer;
	private final WeakPointScoringOrchestrator weakPointScoringOrchestrator;

	// Grades and logs every submitted answer, then for each one: (a) scores it directly against
	// the Java scoring engine and pushes the result straight into the owning domain's weak-point
	// table - this is what lets the redo flow update a score without depending on ai-service/Kafka
	// - and (b) still refreshes mistake_history's occurrence/recency bookkeeping (now done inside
	// the orchestrator, ordered before that item's score is computed). After the loop, still
	// bundles the learner's full current history into one learning.gap.analysis.requested event so
	// ai-service also recomputes and recommendation-service/dashboard-service (which only consume
	// learning.gap.analyzed, not this new Java path) stay up to date; the score_source guard on
	// each domain's upsert keeps that slower, Python-sourced recompute from clobbering a fresher
	// Java-computed score for the same item.
	@Override
	@Transactional
	public void redo(PracticeRedoRequest request) {
		String userId = request.getUserId();
		String recordingId = "practice-" + UUID.randomUUID();
		Set<String> seenItemIdsInBatch = new HashSet<>();

		for (PracticeAttemptRequest attempt : request.getAttempts()) {
			practiceAttemptMapper.insert(PracticeAttempt.builder()
					.userId(userId)
					.itemId(attempt.getItemId())
					.category(attempt.getCategory())
					.label(attempt.getLabel())
					.correct(attempt.isCorrect())
					.build());

			boolean recurredInBatch = !seenItemIdsInBatch.add(attempt.getItemId());
			weakPointScoringOrchestrator.scoreAttempt(userId, recordingId, attempt, recurredInBatch);
		}

		AnalysisRequestedEvent analysisRequestedEvent = AnalysisRequestedEvent.builder()
				.recordingId(recordingId)
				.userId(userId)
				.segments(Collections.emptyList())
				.history(buildHistoryPayload(userId))
				.build();
		analysisRequestedProducer.publish(analysisRequestedEvent);

		log.info("Graded {} redo attempt(s) for user {}, requested re-analysis of {} history item(s)",
				request.getAttempts().size(), userId, analysisRequestedEvent.getHistory().size());
	}

	// Reads items due for review straight off mistake_history's Leitner schedule columns.
	@Override
	public List<ReviewQueueItem> getReviewQueue(String userId) {
		return mistakeHistoryMapper.findDueForReview(userId, Instant.now()).stream()
				.map(entry -> ReviewQueueItem.builder()
						.itemId(entry.getItemId())
						.category(entry.getCategory())
						.label(entry.getLabel())
						.lastWeakScore(entry.getLastWeakScore())
						.nextReviewAt(entry.getNextReviewAt())
						.build())
				.toList();
	}

	// Bundles the learner's history for re-analysis, projecting each row's scoring-engine state and
	// its population-level difficulty counts onto the payload so ai-service's ScoringEngineAnalyzer
	// can reproduce the same composite score the Java-direct path computes (both flows stay consistent).
	private List<MistakeHistoryItemPayload> buildHistoryPayload(String userId) {
		Instant now = Instant.now();
		return mistakeHistoryMapper.findByUserId(userId).stream()
				.map(entry -> {
					String labelKey = LabelKeys.normalize(entry.getLabel());
					PopulationStats stats = itemDifficultyStatsMapper.find(entry.getCategory(), labelKey)
							.orElseGet(() -> PopulationStats.builder().build());
					return MistakeHistoryItemPayload.builder()
							.itemId(entry.getItemId())
							.category(entry.getCategory())
							.label(entry.getLabel())
							.occurrenceCount(entry.getOccurrenceCount())
							.lastSeenDaysAgo(Duration.between(entry.getLastSeenAt(), now).toSeconds() / 86400.0)
							.halfLifeDays(entry.getHalfLifeDays())
							.easeFactor(entry.getEaseFactor())
							.mastery(entry.getMastery())
							.leitnerBox(entry.getLeitnerBox())
							.correctCount(stats.getCorrectCount())
							.incorrectCount(stats.getIncorrectCount())
							.build();
				})
				.toList();
	}
}
