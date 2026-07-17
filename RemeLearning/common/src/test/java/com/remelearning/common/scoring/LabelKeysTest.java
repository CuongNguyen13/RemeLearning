package com.remelearning.common.scoring;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class LabelKeysTest {

	@Test
	void trimsCollapsesWhitespaceAndLowercases() {
		assertThat(LabelKeys.normalize("  Past   Perfect  Tense ")).isEqualTo("past perfect tense");
	}

	@Test
	void doesNotStripCategoryPrefix() {
		assertThat(LabelKeys.normalize("word: Reluctant")).isEqualTo("word: reluctant");
	}

	@Test
	void nullBecomesEmptyString() {
		assertThat(LabelKeys.normalize(null)).isEqualTo("");
	}
}
