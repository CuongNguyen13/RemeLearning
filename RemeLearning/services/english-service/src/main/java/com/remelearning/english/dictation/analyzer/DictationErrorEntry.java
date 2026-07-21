package com.remelearning.english.dictation.analyzer;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** One row of the mistake-comparison table: original vs. what the learner typed, root-classified. */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DictationErrorEntry {
	private String original;
	private String transcribed;
	private DictationErrorCategory category;
	/** Short note on why this was likely misheard/mistyped; may be null. */
	private String note;
}
