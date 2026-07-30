package com.remelearning.english.writing.domain;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * One labelled mistake the AI grader found in a submission. The {@code label} + {@code category}
 * pair is the whole point of this class: it is what gets turned into a
 * {@code PracticeAttemptRequest} so the mistake lands in the learner's existing
 * grammar/vocabulary weak points, review queue and recommendations.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class WritingErrorItem {
	/** The learner's incorrect span, quoted from their own text. */
	private String wrong;
	/** The same span, corrected. */
	private String corrected;
	/** The reusable weak-point label, e.g. "past perfect" or "collocation: make/do". */
	private String label;
	/** {@code "grammar"} or {@code "vocabulary"} - anything else is not routed to a weak point. */
	private String category;
	/** Why it's wrong, in Vietnamese. */
	private String explanationVi;
	/** {@code "minor"} | {@code "major"} - display only, does not affect scoring. */
	private String severity;
}
