package com.remelearning.english.dictation.dto;

import lombok.Data;

/**
 * Facets for generating one AI-practice passage. Each of {@code level}/{@code examType} may be a
 * concrete value (e.g. "B1", "TOEIC"), the literal {@code "RANDOM"} (resolved server-side to one
 * concrete value so the caller always learns what was picked via the returned item's own fields), or
 * null/blank (no preference - the LLM picks freely, matching the pre-existing default behavior).
 * {@code translationLang} is the learner's current UI language ("en"/"vi"); a translation is only
 * generated when it's not "en" (the content's own language).
 */
@Data
public class GenerateAiPracticeRequest {
	private String level;
	private String examType;
	private String translationLang;
}
