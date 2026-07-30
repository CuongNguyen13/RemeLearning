package com.remelearning.english.writing.generator;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class WritingExamProfileTest {

	@Test
	void resolvesTheKnownExamStylesCaseInsensitively() {
		assertThat(WritingExamProfile.fromExamType("TOEIC")).isEqualTo(WritingExamProfile.TOEIC);
		assertThat(WritingExamProfile.fromExamType("ielts")).isEqualTo(WritingExamProfile.IELTS);
		assertThat(WritingExamProfile.fromExamType(" ToEfL ")).isEqualTo(WritingExamProfile.TOEFL);
		assertThat(WritingExamProfile.fromExamType("vstep")).isEqualTo(WritingExamProfile.VSTEP);
	}

	@Test
	void fallsBackToGeneralForNothingOrSomethingUnknown() {
		// An exam style the frontend adds before the backend knows it must still produce a usable task.
		assertThat(WritingExamProfile.fromExamType(null)).isEqualTo(WritingExamProfile.GENERAL);
		assertThat(WritingExamProfile.fromExamType("")).isEqualTo(WritingExamProfile.GENERAL);
		assertThat(WritingExamProfile.fromExamType("   ")).isEqualTo(WritingExamProfile.GENERAL);
		assertThat(WritingExamProfile.fromExamType("Cambridge FCE")).isEqualTo(WritingExamProfile.GENERAL);
	}

	@Test
	void toeicPassagesAreShorterThanIeltsOnes() {
		// This is the whole point of the profile: a TOEIC translation is a short practical text, an
		// IELTS one a longer academic passage. If the ranges ever overlap fully, the distinction is lost.
		assertThat(WritingExamProfile.TOEIC.maxSentences())
				.isLessThan(WritingExamProfile.IELTS.maxSentences());
		assertThat(WritingExamProfile.TOEIC.minSentences())
				.isLessThan(WritingExamProfile.IELTS.minSentences());
	}

	@Test
	void everyProfileDrawsSentenceCountsInsideItsOwnRange() {
		for (WritingExamProfile profile : WritingExamProfile.values()) {
			for (int i = 0; i < 50; i++) {
				assertThat(profile.randomSentenceCount())
						.isBetween(profile.minSentences(), profile.maxSentences());
			}
		}
	}

	@Test
	void sentenceCountActuallyVariesAcrossGenerations() {
		// A learner doing ten TOEIC translations should not get ten identically-shaped passages.
		Set<Integer> seen = new HashSet<>();
		for (int i = 0; i < 200; i++) {
			seen.add(WritingExamProfile.IELTS.randomSentenceCount());
		}
		assertThat(seen).hasSizeGreaterThan(1);
	}

	@Test
	void everyProfileOffersTopicsAndComposeFormatsAndARegister() {
		for (WritingExamProfile profile : WritingExamProfile.values()) {
			assertThat(profile.randomTopic()).isNotBlank();
			assertThat(profile.randomComposeFormat()).isNotBlank();
			assertThat(profile.registerHint()).isNotBlank();
		}
	}

	@Test
	void toeicTopicsAreWorkplaceOrientedAndIeltsOnesAcademic() {
		// Sampled rather than asserted per-entry: the point is the pools are genuinely different,
		// not that any single string is present.
		Set<String> toeicTopics = new HashSet<>();
		Set<String> ieltsTopics = new HashSet<>();
		for (int i = 0; i < 200; i++) {
			toeicTopics.add(WritingExamProfile.TOEIC.randomTopic());
			ieltsTopics.add(WritingExamProfile.IELTS.randomTopic());
		}
		assertThat(toeicTopics).doesNotContainAnyElementsOf(ieltsTopics);
	}
}
