package com.remelearning.english.practice.scoring.impl;

import com.remelearning.common.scoring.BktParams;
import com.remelearning.common.scoring.PopulationStats;
import com.remelearning.english.practice.domain.MistakeHistoryEntry;
import com.remelearning.english.practice.dto.PracticeAttemptRequest;
import com.remelearning.english.practice.mapper.ItemDifficultyStatsMapper;
import com.remelearning.english.practice.mapper.MistakeHistoryMapper;
import com.remelearning.english.practice.scoring.WeakPointDispatcher;
import com.remelearning.english.practice.scoring.WeakPointScoreUpdate;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class WeakPointScoringOrchestratorImplTest {

	private final MistakeHistoryMapper mistakeHistoryMapper = mock(MistakeHistoryMapper.class);
	private final ItemDifficultyStatsMapper itemDifficultyStatsMapper = mock(ItemDifficultyStatsMapper.class);
	private final Map<String, BktParams> bktParamsByCategory = Map.of(
			"vocabulary", BktParams.DEFAULT, "grammar", BktParams.DEFAULT, "pronunciation", BktParams.DEFAULT);
	private final WeakPointDispatcher weakPointDispatcher = mock(WeakPointDispatcher.class);
	private final WeakPointScoringOrchestratorImpl orchestrator = new WeakPointScoringOrchestratorImpl(
			mistakeHistoryMapper, itemDifficultyStatsMapper, bktParamsByCategory, weakPointDispatcher);

	@Test
	void firstEverAttemptUsesColdStartDefaultsAndDispatchesTheComputedScore() {
		when(mistakeHistoryMapper.findOneForUpdate("user-1", "item-1")).thenReturn(Optional.empty());
		when(itemDifficultyStatsMapper.find(anyString(), anyString())).thenReturn(Optional.empty());
		PracticeAttemptRequest attempt = attempt("item-1", "vocabulary", "reluctant", false);

		orchestrator.scoreAttempt("user-1", "practice-abc", attempt, false);

		verify(mistakeHistoryMapper).recordAttempt("user-1", "item-1", "vocabulary", "reluctant", false);
		verify(mistakeHistoryMapper).updateScoringState(eq("user-1"), eq("item-1"), anyDouble(), anyDouble(), anyDouble(),
				anyInt(), any(), anyDouble(), eq("reluctant"));

		ArgumentCaptor<WeakPointScoreUpdate> updateCaptor = ArgumentCaptor.forClass(WeakPointScoreUpdate.class);
		verify(weakPointDispatcher).dispatch(updateCaptor.capture());

		WeakPointScoreUpdate update = updateCaptor.getValue();
		assertThat(update.getUserId()).isEqualTo("user-1");
		assertThat(update.getItemId()).isEqualTo("item-1");
		assertThat(update.getCategory()).isEqualTo("vocabulary");
		assertThat(update.getRecordingId()).isEqualTo("practice-abc");
		// A truly first-ever attempt has daysSincePriorReview=0 (nothing to forget yet on a
		// brand-new item), so forgetting=0 and weakScore=0 is the correct, non-degenerate result.
		assertThat(update.getWeakScore()).isEqualTo(0.0);
		assertThat(update.getMasteryLevel()).isGreaterThan(0.0);
	}

	@Test
	void existingScoringStateIsReusedInsteadOfColdStartDefaults() {
		MistakeHistoryEntry existing = MistakeHistoryEntry.builder()
				.userId("user-1").itemId("item-1").category("vocabulary").label("reluctant")
				.occurrenceCount(10)
				.lastSeenAt(Instant.now().minus(5, ChronoUnit.DAYS))
				.easeFactor(2.8).halfLifeDays(14.0).mastery(0.7).leitnerBox(3)
				.nextReviewAt(Instant.now().minus(1, ChronoUnit.DAYS)) // already scored by the Java engine before
				.build();
		when(mistakeHistoryMapper.findOneForUpdate("user-1", "item-1")).thenReturn(Optional.of(existing));
		when(itemDifficultyStatsMapper.find(anyString(), anyString()))
				.thenReturn(Optional.of(PopulationStats.builder().correctCount(5).incorrectCount(5).build()));

		orchestrator.scoreAttempt("user-1", "practice-abc", attempt("item-1", "vocabulary", "reluctant", true), false);

		ArgumentCaptor<Double> masteryCaptor = ArgumentCaptor.forClass(Double.class);
		verify(mistakeHistoryMapper).updateScoringState(eq("user-1"), eq("item-1"), anyDouble(), anyDouble(),
				masteryCaptor.capture(), anyInt(), any(), anyDouble(), anyString());
		// Starting mastery was 0.7 (persisted), not the 0.3 cold-start default - a correct answer
		// should push it higher still, well above what a cold start from mastery=0.3 could reach.
		assertThat(masteryCaptor.getValue()).isGreaterThan(0.7);
	}

	@Test
	void incrementsPopulationStatsCorrectlyForCorrectAndIncorrectAnswers() {
		when(mistakeHistoryMapper.findOneForUpdate(anyString(), anyString())).thenReturn(Optional.empty());
		when(itemDifficultyStatsMapper.find(anyString(), anyString())).thenReturn(Optional.empty());

		orchestrator.scoreAttempt("user-1", "practice-abc", attempt("item-1", "vocabulary", "reluctant", true), false);
		verify(itemDifficultyStatsMapper).upsertIncrement("vocabulary", "reluctant", 1, 0);

		orchestrator.scoreAttempt("user-1", "practice-abc", attempt("item-2", "vocabulary", "adamant", false), false);
		verify(itemDifficultyStatsMapper).upsertIncrement("vocabulary", "adamant", 0, 1);
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
