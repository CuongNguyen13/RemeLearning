package com.remelearning.english.grammar.service;

import com.remelearning.english.grammar.classifier.GrammarClassifier;
import com.remelearning.english.grammar.domain.GrammarType;
import com.remelearning.english.grammar.domain.GrammarWeakPoint;
import com.remelearning.common.event.LearningGapAnalyzedEvent;
import com.remelearning.common.event.WeakPointPayload;
import com.remelearning.english.grammar.mapper.GrammarWeakPointMapper;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class GrammarWeakPointServiceImplTest {

	private final GrammarWeakPointMapper mapper = mock(GrammarWeakPointMapper.class);
	private final GrammarClassifier classifier = mock(GrammarClassifier.class);
	private final GrammarWeakPointServiceImpl service = new GrammarWeakPointServiceImpl(mapper, classifier);

	@Test
	void savesOnlyWeakPointsWithGrammarCategoryAndSkipsOthers() {
		WeakPointPayload grammarItem = weakPoint("grammar", "Past tense error");
		WeakPointPayload vocabularyItem = weakPoint("vocabulary", "reluctant");
		WeakPointPayload pronunciationItem = weakPoint("pronunciation", "th sound");
		LearningGapAnalyzedEvent event = new LearningGapAnalyzedEvent();
		event.setRecordingId("rec-1");
		event.setUserId("user-1");
		event.setWeakPoints(List.of(grammarItem, vocabularyItem, pronunciationItem));

		when(classifier.classify("Past tense error")).thenReturn(GrammarType.TENSE);

		service.saveWeakPoints(event);

		verify(mapper, times(1)).upsert(any(GrammarWeakPoint.class));
		verify(classifier, never()).classify("reluctant");
		verify(classifier, never()).classify("th sound");
	}

	@Test
	void categoryFilterIsCaseInsensitive() {
		WeakPointPayload item = weakPoint("GRAMMAR", "Article error");
		LearningGapAnalyzedEvent event = new LearningGapAnalyzedEvent();
		event.setRecordingId("rec-1");
		event.setUserId("user-1");
		event.setWeakPoints(List.of(item));
		when(classifier.classify("Article error")).thenReturn(GrammarType.ARTICLE);

		service.saveWeakPoints(event);

		verify(mapper, times(1)).upsert(any(GrammarWeakPoint.class));
	}

	@Test
	void getWeakPointsDelegatesToMapperWithOptionalTypeFilter() {
		List<GrammarWeakPoint> expected = List.of(GrammarWeakPoint.builder().userId("user-1").build());
		when(mapper.findByUserId("user-1", "TENSE")).thenReturn(expected);

		List<GrammarWeakPoint> actual = service.getWeakPoints("user-1", GrammarType.TENSE);

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
