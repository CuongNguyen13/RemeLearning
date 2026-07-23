package com.remelearning.english.speaking.library.dto;

/** Result of finishing a section: whether every sentence passed, plus whether the topic was just passed/unlocked the next one. */
public class FinishSectionResponse {
	private int totalSentences;
	private int passedSentences;
	private boolean passed;
	private Long nextTopicId;
	private boolean nextTopicUnlocked;

	public FinishSectionResponse(int totalSentences, int passedSentences, boolean passed, Long nextTopicId, boolean nextTopicUnlocked) {
		this.totalSentences = totalSentences;
		this.passedSentences = passedSentences;
		this.passed = passed;
		this.nextTopicId = nextTopicId;
		this.nextTopicUnlocked = nextTopicUnlocked;
	}

	public int getTotalSentences() { return totalSentences; }
	public int getPassedSentences() { return passedSentences; }
	public boolean isPassed() { return passed; }
	public Long getNextTopicId() { return nextTopicId; }
	public boolean isNextTopicUnlocked() { return nextTopicUnlocked; }
}
