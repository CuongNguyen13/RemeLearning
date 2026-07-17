package com.remelearning.english.vocabulary.service;

import com.remelearning.english.vocabulary.classifier.VocabularyClassifier;
import com.remelearning.english.vocabulary.domain.VocabularyType;
import com.remelearning.english.vocabulary.domain.VocabularyWeakPoint;
import com.remelearning.common.event.LearningGapAnalyzedEvent;
import com.remelearning.common.event.WeakPointPayload;
import com.remelearning.common.scoring.ScoreSource;
import com.remelearning.english.practice.scoring.WeakPointScoreUpdate;
import com.remelearning.english.vocabulary.mapper.VocabularyWeakPointMapper;
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

class VocabularyWeakPointServiceImplTest {

	private final VocabularyWeakPointMapper mapper = mock(VocabularyWeakPointMapper.class);
	private final VocabularyClassifier classifier = mock(VocabularyClassifier.class);
	private final VocabularyWeakPointServiceImpl service = new VocabularyWeakPointServiceImpl(mapper, classifier);

	@Test
	void savesOnlyWeakPointsWithVocabularyCategoryAndSkipsOthers() {
		WeakPointPayload vocabularyItem = weakPoint("vocabulary", "reluctant");
		WeakPointPayload grammarItem = weakPoint("grammar", "past tense");
		WeakPointPayload pronunciationItem = weakPoint("pronunciation", "th sound");
		LearningGapAnalyzedEvent event = new LearningGapAnalyzedEvent();
		event.setRecordingId("rec-1");
		event.setUserId("user-1");
		event.setWeakPoints(List.of(vocabularyItem, grammarItem, pronunciationItem));

		when(classifier.classify("reluctant")).thenReturn(VocabularyType.ADJECTIVE);

		service.saveWeakPoints(event);

		verify(mapper, times(1)).upsert(any(VocabularyWeakPoint.class));
		verify(classifier, never()).classify("past tense");
		verify(classifier, never()).classify("th sound");
	}

	@Test
	void categoryFilterIsCaseInsensitive() {
		WeakPointPayload item = weakPoint("VOCABULARY", "adamant");
		LearningGapAnalyzedEvent event = new LearningGapAnalyzedEvent();
		event.setRecordingId("rec-1");
		event.setUserId("user-1");
		event.setWeakPoints(List.of(item));
		when(classifier.classify("adamant")).thenReturn(VocabularyType.ADJECTIVE);

		service.saveWeakPoints(event);

		verify(mapper, times(1)).upsert(any(VocabularyWeakPoint.class));
	}

	@Test
	void savedWeakPointIsMarkedPythonLegacy() {
		WeakPointPayload item = weakPoint("vocabulary", "reluctant");
		LearningGapAnalyzedEvent event = new LearningGapAnalyzedEvent();
		event.setRecordingId("rec-1");
		event.setUserId("user-1");
		event.setWeakPoints(List.of(item));
		when(classifier.classify("reluctant")).thenReturn(VocabularyType.ADJECTIVE);

		service.saveWeakPoints(event);

		ArgumentCaptor<VocabularyWeakPoint> captor = ArgumentCaptor.forClass(VocabularyWeakPoint.class);
		verify(mapper).upsert(captor.capture());
		assertThat(captor.getValue().getScoreSource()).isEqualTo(ScoreSource.PYTHON_LEGACY);
	}

	@Test
	void getWeakPointsDelegatesToMapperWithOptionalTypeFilter() {
		List<VocabularyWeakPoint> expected = List.of(VocabularyWeakPoint.builder().userId("user-1").build());
		when(mapper.findByUserId("user-1", "ADJECTIVE")).thenReturn(expected);

		List<VocabularyWeakPoint> actual = service.getWeakPoints("user-1", VocabularyType.ADJECTIVE);

		assertThat(actual).isEqualTo(expected);
	}

	@Test
	void getTopWeakPointsDelegatesToMapperWithLimit() {
		List<VocabularyWeakPoint> expected = List.of(VocabularyWeakPoint.builder().userId("user-1").build());
		when(mapper.findTopByUserId("user-1", 5)).thenReturn(expected);

		List<VocabularyWeakPoint> actual = service.getTopWeakPoints("user-1", 5);

		assertThat(actual).isEqualTo(expected);
	}

	@Test
	void applyJavaComputedScoreUpsertsWithJavaEngineSourceForVocabularyCategory() {
		when(classifier.classify("reluctant")).thenReturn(VocabularyType.ADJECTIVE);
		Instant nextReviewAt = Instant.now().plusSeconds(3600);
		WeakPointScoreUpdate update = WeakPointScoreUpdate.builder()
				.recordingId("practice-abc")
				.userId("user-1")
				.itemId("item-1")
				.category("vocabulary")
				.label("reluctant")
				.weakScore(0.55)
				.masteryLevel(0.5)
				.nextReviewAt(nextReviewAt)
				.build();

		service.applyJavaComputedScore(update);

		ArgumentCaptor<VocabularyWeakPoint> captor = ArgumentCaptor.forClass(VocabularyWeakPoint.class);
		verify(mapper).upsert(captor.capture());
		VocabularyWeakPoint saved = captor.getValue();
		assertThat(saved.getForgettingScore()).isEqualTo(0.55);
		assertThat(saved.getMasteryLevel()).isEqualTo(0.5);
		assertThat(saved.getNextReviewAt()).isEqualTo(nextReviewAt);
		assertThat(saved.getScoreSource()).isEqualTo(ScoreSource.JAVA_ENGINE);
		assertThat(saved.getVocabularyType()).isEqualTo(VocabularyType.ADJECTIVE);
	}

	@Test
	void applyJavaComputedScoreSkipsUpdatesForOtherCategories() {
		WeakPointScoreUpdate update = WeakPointScoreUpdate.builder()
				.userId("user-1").itemId("item-1").category("grammar").label("past tense").weakScore(0.5).build();

		service.applyJavaComputedScore(update);

		verify(mapper, never()).upsert(any());
	}

	private WeakPointPayload weakPoint(String category, String label) {
		WeakPointPayload payload = new WeakPointPayload();
		payload.setItemId("item-1");
		payload.setCategory(category);
		payload.setLabel(label);
		payload.setForgettingScore(0.5);
		payload.setRecommendation("Review " + label);
		return payload;
	}
}
