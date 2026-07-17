package com.remelearning.recommendation.service.impl;

import com.remelearning.common.constants.KafkaTopics;
import com.remelearning.common.queue.BaseEvent;
import com.remelearning.common.queue.EventPublisher;
import com.remelearning.recommendation.domain.Recommendation;
import com.remelearning.common.event.LearningGapAnalyzedEvent;
import com.remelearning.common.event.RecommendationGeneratedEvent;
import com.remelearning.recommendation.exercise.ExerciseGenerator;
import com.remelearning.recommendation.mapper.RecommendationMapper;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RecommendationServiceImplTest {

	private final RecommendationMapper mapper = mock(RecommendationMapper.class);
	private final EventPublisher eventPublisher = mock(EventPublisher.class);
	private final ExerciseGenerator exerciseGenerator = mock(ExerciseGenerator.class);
	private final RecommendationServiceImpl service = new RecommendationServiceImpl(mapper, eventPublisher, exerciseGenerator);

	@Test
	void handleLearningGapAnalyzedUpsertsEveryWeakPointAndPublishesOnce() {
		com.remelearning.common.event.WeakPointPayload vocabularyItem =
				weakPoint("item-1", "vocabulary", "reluctant", 0.8, "Review the word reluctant");
		com.remelearning.common.event.WeakPointPayload grammarItem =
				weakPoint("item-2", "grammar", "Past tense error", 0.6, "Practice past tense");

		LearningGapAnalyzedEvent event = new LearningGapAnalyzedEvent();
		event.setRecordingId("rec-1");
		event.setUserId("user-1");
		event.setWeakPoints(List.of(vocabularyItem, grammarItem));

		when(exerciseGenerator.generate(anyString(), anyString(), anyDouble()))
				.thenReturn(List.of("exercise A", "exercise B"));

		service.handleLearningGapAnalyzed(event);

		// Every weak point is persisted regardless of category - no filtering, unlike
		// english-service's per-domain services.
		var recommendationCaptor = org.mockito.ArgumentCaptor.forClass(Recommendation.class);
		verify(mapper, times(2)).upsert(recommendationCaptor.capture());
		assertThat(recommendationCaptor.getAllValues())
				.allSatisfy(rec -> assertThat(rec.getExercises()).containsExactly("exercise A", "exercise B"));

		var captor = org.mockito.ArgumentCaptor.forClass(BaseEvent.class);
		verify(eventPublisher, times(1)).publish(eq(KafkaTopics.RECOMMENDATION_GENERATED), eq("user-1"), captor.capture());

		assertThat(captor.getValue()).isInstanceOf(RecommendationGeneratedEvent.class);
		RecommendationGeneratedEvent published = (RecommendationGeneratedEvent) captor.getValue();
		assertThat(published.getRecordingId()).isEqualTo("rec-1");
		assertThat(published.getUserId()).isEqualTo("user-1");
		assertThat(published.getRecommendations()).hasSize(2);
		assertThat(published.getRecommendations())
				.extracting("itemId", "category", "label")
				.containsExactlyInAnyOrder(
						org.assertj.core.groups.Tuple.tuple("item-1", "vocabulary", "reluctant"),
						org.assertj.core.groups.Tuple.tuple("item-2", "grammar", "Past tense error"));
		// Same exercises list on both the persisted row and the published payload for a given item -
		// generate() must be called once per weak point, not once for the DB write and again for the
		// publish, since the LLM-backed generator is non-deterministic.
		assertThat(published.getRecommendations())
				.allSatisfy(payload -> assertThat(payload.getExercises()).containsExactly("exercise A", "exercise B"));
		verify(exerciseGenerator, times(2)).generate(anyString(), anyString(), anyDouble());
	}

	@Test
	void getByUserIdDelegatesToMapperWithOptionalCategoryFilter() {
		List<Recommendation> expected = List.of(Recommendation.builder().userId("user-1").build());
		when(mapper.findByUserId("user-1", "grammar")).thenReturn(expected);

		List<Recommendation> actual = service.getByUserId("user-1", "grammar");

		assertThat(actual).isEqualTo(expected);
	}

	@Test
	void getByUserIdGroupedGroupsResultsByCategory() {
		Recommendation vocabRec = Recommendation.builder().userId("user-1").category("vocabulary").itemId("item-1").build();
		Recommendation grammarRec = Recommendation.builder().userId("user-1").category("grammar").itemId("item-2").build();
		when(mapper.findByUserId("user-1", null)).thenReturn(List.of(vocabRec, grammarRec));

		var grouped = service.getByUserIdGrouped("user-1");

		assertThat(grouped).containsOnlyKeys("vocabulary", "grammar");
		assertThat(grouped.get("vocabulary")).containsExactly(vocabRec);
		assertThat(grouped.get("grammar")).containsExactly(grammarRec);
	}

	private com.remelearning.common.event.WeakPointPayload weakPoint(
			String itemId, String category, String label, double forgettingScore, String recommendation) {
		com.remelearning.common.event.WeakPointPayload payload = new com.remelearning.common.event.WeakPointPayload();
		payload.setItemId(itemId);
		payload.setCategory(category);
		payload.setLabel(label);
		payload.setForgettingScore(forgettingScore);
		payload.setRecommendation(recommendation);
		return payload;
	}
}
