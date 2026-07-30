package com.remelearning.english.writing.dto;

import com.remelearning.english.writing.domain.WritingCriteriaScores;
import com.remelearning.english.writing.domain.WritingErrorItem;
import lombok.Builder;
import lombok.Data;

import java.util.List;

/** The graded result of one submission, returned straight after {@code POST /attempts}. */
@Data
@Builder
public class WritingAttemptResultDto {
	private Long attemptId;
	/** Java-computed mean of the populated criteria, not whatever the LLM claimed. */
	private double overallScore;
	private WritingCriteriaScores criteria;
	private String correctedText;
	private List<WritingErrorItem> errors;
	/** Overall remarks in Vietnamese. */
	private String feedback;
	/** Only revealed now that the learner has submitted. */
	private String referenceAnswer;
	/** Short Vietnamese next-steps derived from the errors, same idea as listening's actionAdvice. */
	private List<String> actionAdvice;
}
