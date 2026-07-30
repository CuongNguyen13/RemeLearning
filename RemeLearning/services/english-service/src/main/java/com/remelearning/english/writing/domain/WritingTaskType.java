package com.remelearning.english.writing.domain;

/**
 * The three writing-practice modes. One domain covers all three (rather than a separate
 * "translation" domain) because every mode produces the same shape of graded output - labelled
 * grammar/vocabulary errors - and so feeds the exact same weak-point pipeline.
 */
public enum WritingTaskType {

	/** Learner writes an English text from a Vietnamese task brief. */
	COMPOSE("vi", "en"),

	/** Learner translates a Vietnamese source passage into English. */
	TRANSLATE_VI_EN("vi", "en"),

	/** Learner translates an English source passage into Vietnamese. */
	TRANSLATE_EN_VI("en", "vi");

	private final String sourceLang;
	private final String targetLang;

	WritingTaskType(String sourceLang, String targetLang) {
		this.sourceLang = sourceLang;
		this.targetLang = targetLang;
	}

	public String sourceLang() {
		return sourceLang;
	}

	public String targetLang() {
		return targetLang;
	}

	/** True for the two translation modes, where the reference answer must never leak into a hint. */
	public boolean isTranslation() {
		return this != COMPOSE;
	}
}
