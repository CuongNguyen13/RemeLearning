package com.remelearning.english.writing.domain;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * One hint for what the learner could write next. Deliberately NOT a ready-made sentence: it gives
 * a Vietnamese idea plus English structure/phrase scaffolding, so the learner still has to compose
 * the sentence themselves.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class WritingSuggestion {
	/** What idea to develop next, in Vietnamese. */
	private String ideaVi;
	/** The English sentence pattern to reach for, e.g. "Although + clause, + main clause". */
	private String structureHint;
	/** A few English words/collocations that fit, never a full sentence. */
	private List<String> usefulPhrases;
}
