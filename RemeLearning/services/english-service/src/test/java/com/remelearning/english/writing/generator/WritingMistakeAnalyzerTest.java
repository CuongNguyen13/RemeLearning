package com.remelearning.english.writing.generator;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class WritingMistakeAnalyzerTest {

	@Test
	void extractsDistinctLabelsInReportedOrder() {
		String errorsJson = """
				[{"label": "past perfect", "category": "grammar"},
				 {"label": "collocation: make/do", "category": "vocabulary"},
				 {"label": "past perfect", "category": "grammar"}]""";

		assertThat(WritingMistakeAnalyzer.extractMistakeLabels(errorsJson))
				.containsExactly("past perfect", "collocation: make/do");
	}

	@Test
	void trimsLabelsAndSkipsBlankOnes() {
		String errorsJson = """
				[{"label": "  article usage  ", "category": "grammar"},
				 {"label": "   ", "category": "grammar"},
				 {"label": null, "category": "grammar"}]""";

		assertThat(WritingMistakeAnalyzer.extractMistakeLabels(errorsJson)).containsExactly("article usage");
	}

	@Test
	void returnsEmptyForAnEmptyOrAbsentErrorList() {
		assertThat(WritingMistakeAnalyzer.extractMistakeLabels("[]")).isEmpty();
		assertThat(WritingMistakeAnalyzer.extractMistakeLabels("")).isEmpty();
		assertThat(WritingMistakeAnalyzer.extractMistakeLabels("   ")).isEmpty();
		assertThat(WritingMistakeAnalyzer.extractMistakeLabels(null)).isEmpty();
	}

	@Test
	void returnsEmptyRatherThanThrowingOnMalformedJson() {
		// A retry action must degrade to "generate something new" rather than fail, even if an older
		// row's errors column is somehow unparseable.
		assertThat(WritingMistakeAnalyzer.extractMistakeLabels("{not json")).isEmpty();
		assertThat(WritingMistakeAnalyzer.extractMistakeLabels("{\"label\": \"x\"}")).isEmpty();
	}

	@Test
	void ignoresUnknownFieldsSoOlderRowsStillParse() {
		String errorsJson = """
				[{"label": "past perfect", "category": "grammar", "someFutureField": 42}]""";

		assertThat(WritingMistakeAnalyzer.extractMistakeLabels(errorsJson)).isEqualTo(List.of("past perfect"));
	}
}
