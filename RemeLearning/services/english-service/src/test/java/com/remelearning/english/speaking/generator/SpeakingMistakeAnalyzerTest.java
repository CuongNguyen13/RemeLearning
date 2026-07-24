package com.remelearning.english.speaking.generator;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class SpeakingMistakeAnalyzerTest {

	@Test
	void extractWeakPhonemesReturnsEmptyListForNullInput() {
		assertThat(SpeakingMistakeAnalyzer.extractWeakPhonemes(null)).isEmpty();
	}

	@Test
	void extractWeakPhonemesReturnsEmptyListForBlankInput() {
		assertThat(SpeakingMistakeAnalyzer.extractWeakPhonemes("   ")).isEmpty();
	}

	@Test
	void extractWeakPhonemesReturnsEmptyListForEmptyJsonArray() {
		assertThat(SpeakingMistakeAnalyzer.extractWeakPhonemes("[]")).isEmpty();
	}

	@Test
	void extractWeakPhonemesParsesFlatJsonArrayOfIpaSymbols() {
		List<String> phonemes = SpeakingMistakeAnalyzer.extractWeakPhonemes("[\"θ\", \"ð\"]");

		assertThat(phonemes).containsExactly("θ", "ð");
	}
}
