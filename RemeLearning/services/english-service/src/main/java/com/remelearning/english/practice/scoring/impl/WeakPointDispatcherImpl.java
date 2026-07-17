package com.remelearning.english.practice.scoring.impl;

import com.remelearning.english.grammar.service.GrammarWeakPointService;
import com.remelearning.english.practice.scoring.WeakPointDispatcher;
import com.remelearning.english.practice.scoring.WeakPointScoreUpdate;
import com.remelearning.english.pronunciation.service.PronunciationWeakPointService;
import com.remelearning.english.vocabulary.service.VocabularyWeakPointService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class WeakPointDispatcherImpl implements WeakPointDispatcher {

	private final VocabularyWeakPointService vocabularyWeakPointService;
	private final GrammarWeakPointService grammarWeakPointService;
	private final PronunciationWeakPointService pronunciationWeakPointService;

	// Routes by category (case-insensitive), same convention each domain's own Kafka consumer uses.
	@Override
	public void dispatch(WeakPointScoreUpdate update) {
		switch (update.getCategory().toLowerCase()) {
			case "vocabulary" -> vocabularyWeakPointService.applyJavaComputedScore(update);
			case "grammar" -> grammarWeakPointService.applyJavaComputedScore(update);
			case "pronunciation" -> pronunciationWeakPointService.applyJavaComputedScore(update);
			default -> log.warn("Unknown category '{}' for item {} - no domain to dispatch the Java-computed score to",
					update.getCategory(), update.getItemId());
		}
	}
}
