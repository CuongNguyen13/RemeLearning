package com.remelearning.recommendation.exercise;

import java.util.List;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RuleBasedExerciseGeneratorTest {

	private final RuleBasedExerciseGenerator generator = new RuleBasedExerciseGenerator();

	@Test
	void generatesGrammarTemplatesWithLabelFilledIn() {
		// Exact weak point captured from ai-service's /api/v1/analyze response for recording
		// 48c542be-5597-4102-af29-4b665cb1ab08: {"item_id":"past_perfect","category":"grammar",
		// "label":"past perfect tense","forgetting_score":3.0414}.
		List<String> exercises = generator.generate("grammar", "past perfect tense", 3.0414);

		assertThat(exercises).hasSize(3);
		assertThat(exercises).allSatisfy(exercise -> assertThat(exercise).contains("past perfect tense"));
	}

	@Test
	void generatesVocabularyTemplatesWithLabelFilledIn() {
		List<String> exercises = generator.generate("vocabulary", "reluctant", 0.8);

		assertThat(exercises).hasSize(3);
		assertThat(exercises).allSatisfy(exercise -> assertThat(exercise).contains("reluctant"));
	}

	@Test
	void generatesPronunciationTemplatesWithLabelFilledIn() {
		List<String> exercises = generator.generate("pronunciation", "th sound", 1.2);

		assertThat(exercises).hasSize(3);
		assertThat(exercises).allSatisfy(exercise -> assertThat(exercise).contains("th sound"));
	}

	@Test
	void fallsBackToDefaultTemplateForUnknownCategory() {
		List<String> exercises = generator.generate("listening", "note-taking", 0.5);

		assertThat(exercises).hasSize(1);
		assertThat(exercises.get(0)).contains("note-taking");
	}

	@Test
	void fallsBackToDefaultTemplateForNullCategoryInsteadOfThrowing() {
		// A malformed learning.gap.analyzed payload could have a missing category; Map.of() throws
		// on a null-key lookup, so this must be handled explicitly rather than left to getOrDefault.
		List<String> exercises = generator.generate(null, "note-taking", 0.5);

		assertThat(exercises).hasSize(1);
		assertThat(exercises.get(0)).contains("note-taking");
	}
}
