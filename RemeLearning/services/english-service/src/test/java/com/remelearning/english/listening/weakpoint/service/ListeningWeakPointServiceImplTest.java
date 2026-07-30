package com.remelearning.english.listening.weakpoint.service;

import com.remelearning.common.event.LearningGapAnalyzedEvent;
import com.remelearning.common.event.WeakPointPayload;
import com.remelearning.common.scoring.ScoreSource;
import com.remelearning.english.listening.weakpoint.domain.ListeningSourceType;
import com.remelearning.english.listening.weakpoint.domain.ListeningWeakPoint;
import com.remelearning.english.listening.weakpoint.mapper.ListeningWeakPointMapper;
import com.remelearning.english.practice.scoring.WeakPointScoreUpdate;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ListeningWeakPointServiceImplTest {

	private final ListeningWeakPointMapper mapper = mock(ListeningWeakPointMapper.class);
	private final ListeningWeakPointServiceImpl service = new ListeningWeakPointServiceImpl(mapper);

	@Test
	void savesOnlyWeakPointsWithListeningCategoryAndSkipsOthers() {
		WeakPointPayload listeningItem = weakPoint("listening", "dictation:reluctant");
		WeakPointPayload vocabularyItem = weakPoint("vocabulary", "reluctant");
		WeakPointPayload grammarItem = weakPoint("grammar", "past tense error");
		LearningGapAnalyzedEvent event = new LearningGapAnalyzedEvent();
		event.setRecordingId("rec-1");
		event.setUserId("user-1");
		event.setWeakPoints(List.of(listeningItem, vocabularyItem, grammarItem));

		service.saveWeakPoints(event);

		ArgumentCaptor<ListeningWeakPoint> captor = ArgumentCaptor.forClass(ListeningWeakPoint.class);
		verify(mapper, times(1)).upsert(captor.capture());
		ListeningWeakPoint saved = captor.getValue();
		assertThat(saved.getItemId()).isEqualTo("dictation:reluctant");
		assertThat(saved.getSourceType()).isEqualTo(ListeningSourceType.DICTATION);
		assertThat(saved.getScoreSource()).isEqualTo(ScoreSource.PYTHON_LEGACY);
	}

	@Test
	void categoryFilterIsCaseInsensitive() {
		WeakPointPayload item = weakPoint("LISTENING", "dictation:hello");
		LearningGapAnalyzedEvent event = new LearningGapAnalyzedEvent();
		event.setRecordingId("rec-1");
		event.setUserId("user-1");
		event.setWeakPoints(List.of(item));

		service.saveWeakPoints(event);

		verify(mapper, times(1)).upsert(any(ListeningWeakPoint.class));
	}

	@Test
	void getWeakPointsDelegatesToMapperWithOptionalSourceTypeFilter() {
		List<ListeningWeakPoint> expected = List.of(ListeningWeakPoint.builder().userId("user-1").build());
		when(mapper.findByUserId("user-1", "DICTATION")).thenReturn(expected);

		List<ListeningWeakPoint> actual = service.getWeakPoints("user-1", ListeningSourceType.DICTATION);

		assertThat(actual).isEqualTo(expected);
	}

	@Test
	void applyJavaComputedScoreUpsertsWithJavaEngineSourceAndComprehensionTypeForListeningCategory() {
		Instant nextReviewAt = Instant.now().plusSeconds(3600);
		WeakPointScoreUpdate update = WeakPointScoreUpdate.builder()
				.recordingId("practice-abc")
				.userId("user-1")
				.itemId("item-1")
				.category("listening")
				.label("Where did she go?")
				.weakScore(0.42)
				.masteryLevel(0.6)
				.nextReviewAt(nextReviewAt)
				.build();

		service.applyJavaComputedScore(update);

		ArgumentCaptor<ListeningWeakPoint> captor = ArgumentCaptor.forClass(ListeningWeakPoint.class);
		verify(mapper).upsert(captor.capture());
		ListeningWeakPoint saved = captor.getValue();
		assertThat(saved.getForgettingScore()).isEqualTo(0.42);
		assertThat(saved.getMasteryLevel()).isEqualTo(0.6);
		assertThat(saved.getNextReviewAt()).isEqualTo(nextReviewAt);
		assertThat(saved.getScoreSource()).isEqualTo(ScoreSource.JAVA_ENGINE);
		assertThat(saved.getSourceType()).isEqualTo(ListeningSourceType.COMPREHENSION);
		assertThat(saved.getRecommendation()).contains("Where did she go?");
	}

	@Test
	void applyJavaComputedScoreSkipsUpdatesForOtherCategories() {
		WeakPointScoreUpdate update = WeakPointScoreUpdate.builder()
				.userId("user-1").itemId("item-1").category("vocabulary").label("reluctant").weakScore(0.5).build();

		service.applyJavaComputedScore(update);

		verify(mapper, never()).upsert(any());
	}

	private WeakPointPayload weakPoint(String category, String itemId) {
		WeakPointPayload payload = new WeakPointPayload();
		payload.setItemId(itemId);
		payload.setCategory(category);
		payload.setLabel("reluctant");
		payload.setForgettingScore(0.5);
		payload.setRecommendation("Review reluctant");
		return payload;
	}
}
