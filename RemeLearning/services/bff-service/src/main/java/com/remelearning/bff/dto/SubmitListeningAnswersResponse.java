package com.remelearning.bff.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.List;

/** Scoring result for one submitted listening-library answer set, plus whether the topic was just passed/unlocked the next one. */
@Data
public class SubmitListeningAnswersResponse {
	private double score;
	private int correctCount;
	private int totalQuestions;
	private boolean topicPassed;
	private Long nextTopicId;
	private boolean nextTopicUnlocked;
	private List<QuestionResult> questionResults;

	/** Per-question breakdown: what the learner picked vs. the correct option text, for a review list. */
	@Data
	public static class QuestionResult {
		private Long questionId;
		private String questionText;
		private List<String> options;
		private String selectedOption;
		private String correctOption;
		@JsonProperty("isCorrect")
		private boolean correct;
	}
}
