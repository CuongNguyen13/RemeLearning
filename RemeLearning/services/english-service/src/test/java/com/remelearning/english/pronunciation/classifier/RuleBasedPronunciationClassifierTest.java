package com.remelearning.english.pronunciation.classifier;

import com.remelearning.english.pronunciation.domain.PronunciationType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

class RuleBasedPronunciationClassifierTest {

	private final RuleBasedPronunciationClassifier classifier = new RuleBasedPronunciationClassifier();

	@ParameterizedTest
	@CsvSource({
			"Confusion between short and long vowel sounds, VOWEL",
			"Difficulty with th consonant sound, CONSONANT",
			"Wrong word stress on 'photograph', STRESS",
			"Flat intonation in questions, INTONATION",
			"Missing linking between words, LINKING",
			"Unnatural sentence rhythm, RHYTHM",
			"Mumbling unclear speech, OTHER",
	})
	void classifiesByKeywordInLabel(String label, PronunciationType expected) {
		assertThat(classifier.classify(label)).isEqualTo(expected);
	}

	@Test
	void classificationIsCaseInsensitive() {
		assertThat(classifier.classify("WORD STRESS error")).isEqualTo(PronunciationType.STRESS);
	}
}
