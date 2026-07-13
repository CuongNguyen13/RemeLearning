package com.remelearning.vocabulary.classifier;

import com.remelearning.vocabulary.domain.VocabularyType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

class RuleBasedVocabularyClassifierTest {

	private final RuleBasedVocabularyClassifier classifier = new RuleBasedVocabularyClassifier();

	@ParameterizedTest
	@CsvSource({
			"word: happily, ADVERB",
			"word: reluctant, ADJECTIVE",
			"word: information, NOUN",
			"word: dancing, VERB",
			"word: cat, OTHER",
	})
	void classifiesSingleWordsByPartOfSpeech(String label, VocabularyType expected) {
		assertThat(classifier.classify(label)).isEqualTo(expected);
	}

	@ParameterizedTest
	@CsvSource({
			"phrase: give up, PHRASAL_VERB",
			"phrase: look after, PHRASAL_VERB",
			"phrase: make a decision, COLLOCATION",
			"phrase: heavy rain, COLLOCATION",
			"phrase: once in a blue moon, IDIOM",
	})
	void classifiesMultiWordPhrases(String label, VocabularyType expected) {
		assertThat(classifier.classify(label)).isEqualTo(expected);
	}

	@Test
	void extractsTermAfterLastColonAndIgnoresCase() {
		assertThat(classifier.classify("vocabulary: RELUCTANT")).isEqualTo(VocabularyType.ADJECTIVE);
	}

	@Test
	void classifiesBareLabelWithoutCategoryPrefix() {
		assertThat(classifier.classify("give up")).isEqualTo(VocabularyType.PHRASAL_VERB);
	}
}
