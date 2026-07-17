package com.remelearning.english.dictation.scoring;

import com.remelearning.english.dictation.dto.WordDiffDto;
import com.remelearning.english.dictation.dto.WordDiffTag;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DictationScorerTest {

	@Test
	void perfectTranscriptScoresFullAccuracy() {
		DictationScoreResult result = DictationScorer.score("I need to remember this word.", "I need to remember this word.");

		assertThat(result.getAccuracy()).isEqualTo(1.0);
		assertThat(result.getWer()).isEqualTo(0.0);
		assertThat(result.getDiff()).extracting(WordDiffDto::getTag).containsOnly(WordDiffTag.CORRECT);
	}

	@Test
	void isCaseInsensitiveAndPunctuationNormalized() {
		DictationScoreResult result = DictationScorer.score("She was reluctant to admit her mistake.",
				"SHE was reluctant to admit her mistake");

		assertThat(result.getAccuracy()).isEqualTo(1.0);
		assertThat(result.getDiff()).extracting(WordDiffDto::getTag).containsOnly(WordDiffTag.CORRECT);
	}

	@Test
	void detectsASingleSubstitution() {
		DictationScoreResult result = DictationScorer.score("I need to remember this word.", "I need to forget this word.");

		assertThat(result.getWer()).isEqualTo(1.0 / 6);
		assertThat(result.getDiff())
				.filteredOn(slot -> slot.getTag() == WordDiffTag.SUBSTITUTED)
				.hasSize(1)
				.first()
				.satisfies(slot -> {
					assertThat(slot.getExpectedWord()).isEqualTo("remember");
					assertThat(slot.getActualWord()).isEqualTo("forget");
				});
	}

	@Test
	void detectsAMissingWord() {
		DictationScoreResult result = DictationScorer.score("I need to remember this word.", "I need to remember word.");

		assertThat(result.getDiff())
				.filteredOn(slot -> slot.getTag() == WordDiffTag.MISSING)
				.hasSize(1)
				.first()
				.satisfies(slot -> assertThat(slot.getExpectedWord()).isEqualTo("this"));
	}

	@Test
	void detectsAnExtraWord() {
		DictationScoreResult result = DictationScorer.score("I need to remember this word.", "I really need to remember this word.");

		assertThat(result.getDiff())
				.filteredOn(slot -> slot.getTag() == WordDiffTag.EXTRA)
				.hasSize(1)
				.first()
				.satisfies(slot -> assertThat(slot.getActualWord()).isEqualTo("really"));
	}

	@Test
	void blankTranscriptAgainstNonBlankReferenceScoresZeroAccuracy() {
		DictationScoreResult result = DictationScorer.score("I need to remember this word.", "");

		assertThat(result.getAccuracy()).isEqualTo(0.0);
		assertThat(result.getWer()).isEqualTo(1.0);
		assertThat(result.getDiff()).extracting(WordDiffDto::getTag).containsOnly(WordDiffTag.MISSING);
	}

	@Test
	void blankReferenceAndBlankTranscriptScoresFullAccuracy() {
		DictationScoreResult result = DictationScorer.score("", "");

		assertThat(result.getAccuracy()).isEqualTo(1.0);
		assertThat(result.getWer()).isEqualTo(0.0);
		assertThat(result.getDiff()).isEmpty();
	}
}
