package com.remelearning.english.writing.domain;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Per-criterion scores, each in [0, 1]. The fourth criterion differs by task type: translation
 * modes are scored on {@code accuracy} (how faithfully the meaning carried over), COMPOSE on
 * {@code taskResponse} (how well the brief was addressed). Only the one relevant to the attempt's
 * task type is populated; the other stays null so the UI knows not to show it.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class WritingCriteriaScores {
	private Double grammar;
	private Double vocabulary;
	private Double coherence;
	/** Translation modes only. */
	private Double accuracy;
	/** COMPOSE only. */
	private Double taskResponse;
}
