package com.remelearning.english.grammar.classifier;

import com.remelearning.english.grammar.domain.GrammarType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

class RuleBasedGrammarClassifierTest {

	private final RuleBasedGrammarClassifier classifier = new RuleBasedGrammarClassifier();

	@ParameterizedTest
	@CsvSource({
			"Subject-verb agreement in third person, SUBJECT_VERB_AGREEMENT",
			"Incorrect past tense usage, TENSE",
			"Missing article before singular noun, ARTICLE",
			"Wrong preposition after verb, PREPOSITION",
			"Incorrect word order in questions, WORD_ORDER",
			"Irregular plural form, PLURAL",
			"Missing punctuation at sentence end, PUNCTUATION",
			"Confusing use of modal verbs, OTHER",
	})
	void classifiesByKeywordInLabel(String label, GrammarType expected) {
		assertThat(classifier.classify(label)).isEqualTo(expected);
	}

	@Test
	void classificationIsCaseInsensitive() {
		assertThat(classifier.classify("SUBJECT-VERB AGREEMENT error")).isEqualTo(GrammarType.SUBJECT_VERB_AGREEMENT);
	}
}
