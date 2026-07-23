package com.remelearning.bff.dto;

import lombok.Data;

/** Result of finishing a speaking-library section: whether every sentence passed, plus whether the topic was just passed/unlocked the next one. */
@Data
public class FinishSpeakingSectionResponse {
	private int totalSentences;
	private int passedSentences;
	private boolean passed;
	private Long nextTopicId;
	private boolean nextTopicUnlocked;
}
