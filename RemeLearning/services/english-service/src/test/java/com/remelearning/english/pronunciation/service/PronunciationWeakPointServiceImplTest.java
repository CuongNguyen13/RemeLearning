package com.remelearning.english.pronunciation.service;

import com.remelearning.english.pronunciation.classifier.PronunciationClassifier;
import com.remelearning.english.pronunciation.domain.PronunciationType;
import com.remelearning.english.pronunciation.domain.PronunciationWeakPoint;
import com.remelearning.english.pronunciation.event.LearningGapAnalyzedEvent;
import com.remelearning.english.pronunciation.event.WeakPointPayload;
import com.remelearning.english.pronunciation.mapper.PronunciationWeakPointMapper;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PronunciationWeakPointServiceImplTest {

	private final PronunciationWeakPointMapper mapper = mock(PronunciationWeakPointMapper.class);
	private final PronunciationClassifier classifier = mock(PronunciationClassifier.class);
	private final PronunciationWeakPointServiceImpl service =
			new PronunciationWeakPointServiceImpl(mapper, classifier);

	@Test
	void savesOnlyWeakPointsWithPronunciationCategoryAndSkipsOthers() {
		WeakPointPayload pronunciationItem = weakPoint("pronunciation", "th sound");
		WeakPointPayload vocabularyItem = weakPoint("vocabulary", "reluctant");
		WeakPointPayload grammarItem = weakPoint("grammar", "past tense");
		LearningGapAnalyzedEvent event = new LearningGapAnalyzedEvent();
		event.setRecordingId("rec-1");
		event.setUserId("user-1");
		event.setWeakPoints(List.of(pronunciationItem, vocabularyItem, grammarItem));

		when(classifier.classify("th sound")).thenReturn(PronunciationType.CONSONANT);

		service.saveWeakPoints(event);

		verify(mapper, times(1)).upsert(any(PronunciationWeakPoint.class));
		verify(classifier, never()).classify("reluctant");
		verify(classifier, never()).classify("past tense");
	}

	@Test
	void categoryFilterIsCaseInsensitive() {
		WeakPointPayload item = weakPoint("PRONUNCIATION", "word stress");
		LearningGapAnalyzedEvent event = new LearningGapAnalyzedEvent();
		event.setRecordingId("rec-1");
		event.setUserId("user-1");
		event.setWeakPoints(List.of(item));
		when(classifier.classify("word stress")).thenReturn(PronunciationType.STRESS);

		service.saveWeakPoints(event);

		verify(mapper, times(1)).upsert(any(PronunciationWeakPoint.class));
	}

	@Test
	void getWeakPointsDelegatesToMapperWithOptionalTypeFilter() {
		List<PronunciationWeakPoint> expected = List.of(PronunciationWeakPoint.builder().userId("user-1").build());
		when(mapper.findByUserId("user-1", "STRESS")).thenReturn(expected);

		List<PronunciationWeakPoint> actual = service.getWeakPoints("user-1", PronunciationType.STRESS);

		assertThat(actual).isEqualTo(expected);
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
