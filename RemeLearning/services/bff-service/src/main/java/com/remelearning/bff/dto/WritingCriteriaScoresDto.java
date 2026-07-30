package com.remelearning.bff.dto;

import lombok.Data;

/** Per-criterion writing scores, each in [0, 1]; only the criterion relevant to the task type is set. */
@Data
public class WritingCriteriaScoresDto {
	private Double grammar;
	private Double vocabulary;
	private Double coherence;
	/** Translation tasks only. */
	private Double accuracy;
	/** COMPOSE only. */
	private Double taskResponse;
}
