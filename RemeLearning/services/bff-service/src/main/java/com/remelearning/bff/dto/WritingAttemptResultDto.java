package com.remelearning.bff.dto;

import lombok.Data;

import java.util.List;

/** The graded result of one writing submission, proxied from english-service. */
@Data
public class WritingAttemptResultDto {
	private Long attemptId;
	private double overallScore;
	private WritingCriteriaScoresDto criteria;
	private String correctedText;
	private List<WritingErrorDto> errors;
	private String feedback;
	/** Only present after submission. */
	private String referenceAnswer;
	private List<String> actionAdvice;
}
