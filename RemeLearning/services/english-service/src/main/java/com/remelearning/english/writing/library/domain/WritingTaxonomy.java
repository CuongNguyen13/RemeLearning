package com.remelearning.english.writing.library.domain;

/**
 * The three independent axes the writing library can be browsed along. Progress gating is tracked
 * per axis: a learner can be deep into the grammar chain while still on the first genre topic.
 */
public enum WritingTaxonomy {

	/** The 60-topic grammar taxonomy shared with the grammar/listening libraries. */
	GRAMMAR("grammar"),

	/** Real-world text types: email, IELTS task, report, ... */
	GENRE("genre"),

	/** The vocabulary-library theme set: travel, business, health, ... */
	VOCAB_THEME("vocab_theme");

	private final String code;

	WritingTaxonomy(String code) {
		this.code = code;
	}

	/** The value stored in {@code writing_library_topics.taxonomy}. */
	public String code() {
		return code;
	}

	/** Parses a stored/request value; throws {@link IllegalArgumentException} for anything unknown. */
	public static WritingTaxonomy fromCode(String code) {
		for (WritingTaxonomy taxonomy : values()) {
			if (taxonomy.code.equalsIgnoreCase(code)) {
				return taxonomy;
			}
		}
		throw new IllegalArgumentException("Unknown writing library taxonomy: " + code);
	}
}
