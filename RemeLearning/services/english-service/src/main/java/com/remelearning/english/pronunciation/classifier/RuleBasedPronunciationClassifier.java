package com.remelearning.english.pronunciation.classifier;

import com.remelearning.english.pronunciation.domain.PronunciationType;
import org.springframework.stereotype.Component;

/** Heuristic classifier: keyword matching against the label text, no LLM cost. */
@Component
public class RuleBasedPronunciationClassifier implements PronunciationClassifier {

	@Override
	public PronunciationType classify(String label) {
		String text = label.toLowerCase();
		if (text.contains("vowel")) {
			return PronunciationType.VOWEL;
		}
		if (text.contains("consonant")) {
			return PronunciationType.CONSONANT;
		}
		if (text.contains("stress")) {
			return PronunciationType.STRESS;
		}
		if (text.contains("intonation")) {
			return PronunciationType.INTONATION;
		}
		if (text.contains("link")) {
			return PronunciationType.LINKING;
		}
		if (text.contains("rhythm")) {
			return PronunciationType.RHYTHM;
		}
		return PronunciationType.OTHER;
	}
}
