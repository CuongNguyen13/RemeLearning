package com.remelearning.bff.dto;

import lombok.Data;

import java.util.List;

/** Graded result of one library prompt plus how it moved the learner through the topic chain. */
@Data
public class SubmitWritingLibraryAnswerResponseDto {
	private Long attemptId;
	private double score;
	private boolean passed;
	private WritingCriteriaScoresDto criteria;
	private String correctedText;
	private List<WritingErrorDto> errors;
	private String feedback;
	private String referenceAnswer;
	private List<String> actionAdvice;
	private int passedPromptCount;
	private int targetPromptCount;
	private boolean topicPassed;
	private Long nextTopicId;
	private boolean nextTopicUnlocked;
}
