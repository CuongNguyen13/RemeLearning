package com.remelearning.english.writing.library.dto;

import com.remelearning.english.writing.domain.WritingCriteriaScores;
import com.remelearning.english.writing.domain.WritingErrorItem;
import lombok.Builder;
import lombok.Data;

import java.util.List;

/**
 * The graded result of one library prompt, plus how it moved the learner through the topic chain.
 * Carries the same grader fields as {@code WritingAttemptResultDto} so the FE can reuse one result
 * panel for both the "Học thường" and "Thư viện" tabs.
 */
@Data
@Builder
public class SubmitWritingLibraryAnswerResponse {
	private Long attemptId;
	private double score;
	private boolean passed;
	private WritingCriteriaScores criteria;
	private String correctedText;
	private List<WritingErrorItem> errors;
	private String feedback;
	/** Revealed only now that the learner has submitted. */
	private String referenceAnswer;
	private List<String> actionAdvice;
	/** How many prompts of this topic's chain are now passed, and how many it holds in total. */
	private int passedPromptCount;
	private int targetPromptCount;
	/** True once every prompt in the chain is passed and the topic itself flipped to PASSED. */
	private boolean topicPassed;
	/** The next topic on the SAME axis, if passing this topic just unlocked it. */
	private Long nextTopicId;
	private boolean nextTopicUnlocked;
}
