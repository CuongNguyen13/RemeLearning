package com.remelearning.bff.dto;

import lombok.Data;

/** Scoring result for one recorded speaking-library sentence attempt - does not itself affect topic gating (see FinishSpeakingSectionResponse). */
@Data
public class SentenceAttemptResultDto {
	private Long sentenceId;
	private double phonemeScore;
	private double wordScore;
	private boolean passed;
	private String transcript;
}
