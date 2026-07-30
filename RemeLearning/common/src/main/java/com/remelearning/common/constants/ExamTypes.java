package com.remelearning.common.constants;

import java.util.List;

/**
 * The exam styles a learner can aim their AI-generated practice at, shared so the frontend picker,
 * the practice-session request and each skill's generator all agree on one spelling. Previously each
 * caller passed free text (and {@code DictationServiceImpl} kept its own private fallback list),
 * which meant "toeic", "TOEIC" and "Toeic" produced three different stored values.
 *
 * <p>Kept as plain strings rather than an enum because they cross the REST boundary in both
 * directions and are optional everywhere - an unrecognised value must degrade to
 * {@link #GENERAL}-shaped behaviour, not fail a request.
 */
public final class ExamTypes {

	private ExamTypes() {
	}

	public static final String TOEIC = "TOEIC";
	public static final String IELTS = "IELTS";
	public static final String TOEFL = "TOEFL";
	/** Vietnam's own standardized English test. */
	public static final String VSTEP = "VSTEP";
	/** No exam in mind - everyday English. */
	public static final String GENERAL = "General";

	/** The picker's options, in the order the UI should show them. */
	public static final List<String> COMMON = List.of(TOEIC, IELTS, TOEFL, VSTEP, GENERAL);

	/**
	 * Canonical spelling for a caller-supplied value, case-insensitively; returns {@code null} for
	 * null/blank (meaning "no preference", which every generator already handles) and returns the
	 * input trimmed but otherwise untouched for anything unrecognised, so a new exam style added by
	 * the frontend first isn't silently swallowed.
	 */
	public static String normalize(String examType) {
		if (examType == null || examType.isBlank()) {
			return null;
		}
		String trimmed = examType.trim();
		for (String known : COMMON) {
			if (known.equalsIgnoreCase(trimmed)) {
				return known;
			}
		}
		return trimmed;
	}
}
