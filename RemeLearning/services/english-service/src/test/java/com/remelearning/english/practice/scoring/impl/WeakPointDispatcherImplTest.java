package com.remelearning.english.practice.scoring.impl;

import com.remelearning.english.grammar.service.GrammarWeakPointService;
import com.remelearning.english.practice.scoring.WeakPointScoreUpdate;
import com.remelearning.english.pronunciation.service.PronunciationWeakPointService;
import com.remelearning.english.vocabulary.service.VocabularyWeakPointService;
import org.junit.jupiter.api.Test;

import static org.mockito.Mockito.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class WeakPointDispatcherImplTest {

	private final VocabularyWeakPointService vocabularyWeakPointService = mock(VocabularyWeakPointService.class);
	private final GrammarWeakPointService grammarWeakPointService = mock(GrammarWeakPointService.class);
	private final PronunciationWeakPointService pronunciationWeakPointService = mock(PronunciationWeakPointService.class);
	private final WeakPointDispatcherImpl dispatcher = new WeakPointDispatcherImpl(
			vocabularyWeakPointService, grammarWeakPointService, pronunciationWeakPointService);

	@Test
	void dispatchesVocabularyUpdatesToVocabularyServiceOnly() {
		dispatcher.dispatch(update("vocabulary"));

		verify(vocabularyWeakPointService).applyJavaComputedScore(any());
		verify(grammarWeakPointService, never()).applyJavaComputedScore(any());
		verify(pronunciationWeakPointService, never()).applyJavaComputedScore(any());
	}

	@Test
	void dispatchesGrammarUpdatesToGrammarServiceOnly() {
		dispatcher.dispatch(update("grammar"));

		verify(grammarWeakPointService).applyJavaComputedScore(any());
		verify(vocabularyWeakPointService, never()).applyJavaComputedScore(any());
		verify(pronunciationWeakPointService, never()).applyJavaComputedScore(any());
	}

	@Test
	void dispatchesPronunciationUpdatesToPronunciationServiceOnly() {
		dispatcher.dispatch(update("pronunciation"));

		verify(pronunciationWeakPointService).applyJavaComputedScore(any());
		verify(vocabularyWeakPointService, never()).applyJavaComputedScore(any());
		verify(grammarWeakPointService, never()).applyJavaComputedScore(any());
	}

	@Test
	void categoryMatchingIsCaseInsensitive() {
		dispatcher.dispatch(update("GRAMMAR"));

		verify(grammarWeakPointService).applyJavaComputedScore(any());
	}

	@Test
	void unknownCategoryDoesNotDispatchToAnyDomainOrThrow() {
		dispatcher.dispatch(update("listening"));

		verify(vocabularyWeakPointService, never()).applyJavaComputedScore(any());
		verify(grammarWeakPointService, never()).applyJavaComputedScore(any());
		verify(pronunciationWeakPointService, never()).applyJavaComputedScore(any());
	}

	private WeakPointScoreUpdate update(String category) {
		return WeakPointScoreUpdate.builder()
				.userId("user-1").itemId("item-1").category(category).label("label").weakScore(0.5).build();
	}
}
