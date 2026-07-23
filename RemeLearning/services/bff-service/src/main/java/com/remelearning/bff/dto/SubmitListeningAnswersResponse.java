package com.remelearning.bff.dto;

import lombok.Data;

/** Scoring result for one submitted listening-library answer set, plus whether the topic was just passed/unlocked the next one. */
@Data
public class SubmitListeningAnswersResponse {
	private double score;
	private int correctCount;
	private int totalQuestions;
	private boolean topicPassed;
	private Long nextTopicId;
	private boolean nextTopicUnlocked;
}
