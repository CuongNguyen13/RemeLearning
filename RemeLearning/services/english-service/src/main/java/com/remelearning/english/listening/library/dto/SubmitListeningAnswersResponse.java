package com.remelearning.english.listening.library.dto;

/** Scoring result for one submitted answer set, plus whether the topic was just passed/unlocked the next one. */
public class SubmitListeningAnswersResponse {
	private double score;
	private int correctCount;
	private int totalQuestions;
	private boolean topicPassed;
	private Long nextTopicId;
	private boolean nextTopicUnlocked;

	public SubmitListeningAnswersResponse(double score, int correctCount, int totalQuestions,
			boolean topicPassed, Long nextTopicId, boolean nextTopicUnlocked) {
		this.score = score;
		this.correctCount = correctCount;
		this.totalQuestions = totalQuestions;
		this.topicPassed = topicPassed;
		this.nextTopicId = nextTopicId;
		this.nextTopicUnlocked = nextTopicUnlocked;
	}

	public double getScore() { return score; }
	public int getCorrectCount() { return correctCount; }
	public int getTotalQuestions() { return totalQuestions; }
	public boolean isTopicPassed() { return topicPassed; }
	public Long getNextTopicId() { return nextTopicId; }
	public boolean isNextTopicUnlocked() { return nextTopicUnlocked; }
}
