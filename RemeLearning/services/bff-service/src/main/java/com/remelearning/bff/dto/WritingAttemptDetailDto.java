package com.remelearning.bff.dto;

import lombok.Data;

import java.time.Instant;
import java.util.List;

/** Full detail of one past writing attempt, for the review dialog. */
@Data
public class WritingAttemptDetailDto {
	private Long attemptId;
	private Long practiceItemId;
	private String taskType;
	private String level;
	private String examType;
	private String topic;
	private String promptText;
	private String submittedText;
	private String correctedText;
	private double overallScore;
	private WritingCriteriaScoresDto criteria;
	private List<WritingErrorDto> errors;
	private String feedback;
	private String referenceAnswer;
	private Instant attemptedAt;
}
